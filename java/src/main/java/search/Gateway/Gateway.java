package search.Gateway;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.stream.Collectors;

import javax.sql.rowset.spi.SyncResolver;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

import search.Client.ClientInterface;
import search.Client.TopSearchEntry;
import search.Barrels.BarrelInterface;
import search.Barrels.UrlMetadata;
import search.Downloaders.RobotInterface;
import search.Queue.QueueInterface;
import search.Properties.ConfigProperties;

public class Gateway extends UnicastRemoteObject implements GatewayInterface{
    
    private List<ClientInterface> subscribedClients = new ArrayList<ClientInterface>();
    private List<BarrelInterface> subscribedBarrels = new ArrayList<BarrelInterface>();
    private List<RobotInterface> subscribedRobots = new ArrayList<RobotInterface>();

    private Map<ClientInterface, List<String>> clientSearchCache = new HashMap<>();
    //private Map<ClientInterface, Integer> clientSearchIndex = new HashMap<>();
    private QueueInterface queue;
    private int currentBarrelClient = 0;
    private int currentBarrelBarrel = 0;
    private Map<String, Integer> pesquisas = new HashMap<>();

    public Gateway() throws RemoteException {
        super();
    }

    public static void main(String args[]) {
        ConfigProperties config = new ConfigProperties();
        try {
            
            System.setProperty("java.rmi.server.hostname", config.get("gateway.host"));
            Gateway gateway = new Gateway();
            Registry registry = LocateRegistry.createRegistry(config.getInt("gateway.port"));
            registry.bind(config.get("gateway.name"),gateway);

            Object lock = new Object();
            synchronized (lock) {
                lock.wait();
            }
        
        } catch (Exception e) {
            e.printStackTrace(); 
        }
    }

    // --------------------------------------------------------
    // Proximo Barrel (Round Robin com verificação de vida)
    // --------------------------------------------------------
    
    public synchronized BarrelInterface getNextBarrel(String id) throws RemoteException {
        if (subscribedBarrels.isEmpty()) {
        return null;
    }

    // Garante que os índices nunca ultrapassam o tamanho da lista
    currentBarrelClient = currentBarrelClient % subscribedBarrels.size();
    currentBarrelBarrel = currentBarrelBarrel % subscribedBarrels.size();
    
    int attempts = 0;
    int maxAttempts = subscribedBarrels.size();
    
    while (attempts < maxAttempts) {
        int index = id.equals("client") ? currentBarrelClient : currentBarrelBarrel;
        
        // Verifica novamente antes de aceder
        if (index >= subscribedBarrels.size()) {
            index = 0;
            if (id.equals("client")) {
                currentBarrelClient = 0;
            } else {
                currentBarrelBarrel = 0;
            }
        }
        
        BarrelInterface barrel = subscribedBarrels.get(index);
        
        // Testa se o Barrel está vivo
        try {
            barrel.getBarrelName(); // Chamada simples para verificar conexão
            
            // Se bem-sucedido, atualiza índice e retorna
            if (id.equals("client")) {
                currentBarrelClient = (currentBarrelClient + 1) % subscribedBarrels.size();
            } else {
                currentBarrelBarrel = (currentBarrelBarrel + 1) % subscribedBarrels.size();
            }
            return barrel;
            
        } catch (RemoteException e) {
            System.out.println("[Gateway] Barrel inativo detectado, removendo da lista..." + barrel.getBarrelName());
            subscribedBarrels.remove(index);
            for (ClientInterface cliente : subscribedClients) {
                try {
                    cliente.deleteBarrel(barrel.getBarrelName());
                } catch (RemoteException ex) {
                    System.out.println("[Gateway] Cliente inativo detectado, removendo...");
                    subscribedClients.remove(cliente);
                }
            }
            
            // Ajusta índices se lista ficou vazia
            if (subscribedBarrels.isEmpty()) {
                currentBarrelClient = 0;
                currentBarrelBarrel = 0;
                return null;
            }
            
            // Ajusta índices para não ultrapassar tamanho
            currentBarrelClient = currentBarrelClient % subscribedBarrels.size();
            currentBarrelBarrel = currentBarrelBarrel % subscribedBarrels.size();
        }
        
        attempts++;
    }
    
    return null; // Nenhum Barrel disponível
    }

    // --------------------------------------------------------
    // Estatísticas
    // --------------------------------------------------------

    @Override
    public synchronized void sendMeanTime(double meanTime, String barrelName) throws RemoteException {
        for (ClientInterface cliente : subscribedClients) {
            try {
                cliente.updateAverageTime(meanTime, barrelName);
            } catch (RemoteException e) {
                System.out.println("[Gateway] Cliente inativo detectado, removendo...");
                subscribedClients.remove(cliente);
            }
        }
    }

    @Override
    public synchronized void sendSize(int size, String barrelName) throws RemoteException {
        for (ClientInterface cliente : subscribedClients) {
                try {
                    cliente.updateIndexSize(size, barrelName);
                } catch (RemoteException e) {
                    System.out.println("[Gateway] Cliente inativo detectado, removendo...");
                    subscribedClients.remove(cliente);
                }
            }
    }

    @Override
    public synchronized void sendStatistics(ClientInterface client) throws RemoteException {
        List<TopSearchEntry> top10 = pesquisas.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(entry -> new TopSearchEntry(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        client.updateTopSearches(top10);
        for (BarrelInterface barrel : subscribedBarrels) {
            client.updateAverageTime(barrel.getMeanTime(), barrel.getBarrelName());
            client.updateIndexSize(barrel.getSize(), barrel.getBarrelName());
        }
    }

    @Override
    public synchronized void sendStatisticsBarrel(BarrelInterface barrel) throws RemoteException {
        for (ClientInterface client : subscribedClients) {
            client.updateAverageTime(barrel.getMeanTime(), barrel.getBarrelName());
            client.updateIndexSize(barrel.getSize(), barrel.getBarrelName());
        }
    }

    // --------------------------------------------------------
    // Pesquisas
    // --------------------------------------------------------

    private String cleanWord(String word) {
        // Remove todos os caracteres que não sejam letras (incluindo acentos) ou números
        String cleaned = word.replaceAll("[^a-zA-ZÀ-ÿ0-9]", "");
        // Retorna null se a palavra ficar vazia (era só caracteres especiais)
        return cleaned.isEmpty() ? null : cleaned;
    }

    public String detectLanguage(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return "unknown";
            }
            
            // Apache Tika - usando LanguageDetector
            LanguageDetector detector = new OptimaizeLangDetector().loadModels();
            LanguageResult result = detector.detect(content);
            String language = result.getLanguage();
            
            // Retorna "unknown" se a confiança for muito baixa
            if (result.isReasonablyCertain()) {
                System.out.println(language);
                return language;
            } else {
                return "unknown";
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao detetar linguagem: " + e.getMessage());
            return "unknown";
        }
    }

    @Override
    public Integer searchWordsUrls(ClientInterface client, String line) throws RemoteException {
        // Pesquisa num barrel
        List<String> resultados = new ArrayList<>();
        BarrelInterface barrel = getNextBarrel("client");

        String[] words = line.toLowerCase().split("\\s+"); // divide a frase em palavras

        List<TopSearchEntry> oldTop10 = pesquisas.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(entry -> new TopSearchEntry(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        for (String word : words) {
            String clean = cleanWord(word);
            String lang = detectLanguage(word);
            if (clean == null) continue;
            if (barrel.isStopword(clean, lang)) continue;
            if (pesquisas.containsKey(clean)) {
                int i = pesquisas.get(clean);
                pesquisas.put(clean, i + 1);
            } else {
                pesquisas.put(clean, 1);
            }
        }

        List<TopSearchEntry> newTop10 = pesquisas.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .map(entry -> new TopSearchEntry(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        if (!oldTop10.equals(newTop10)) {
            for (ClientInterface cliente : subscribedClients) {
                cliente.updateTopSearches(newTop10);
            }
        }

        if (!subscribedBarrels.isEmpty()) {
            resultados = barrel.searchWord(line);
        } else {
            System.out.println("Nenhum Barrel disponível!");
        }

        int numpages = (int) Math.ceil(resultados.size() / 10.0);

        // Armazena os resultados no cache do cliente
        clientSearchCache.put(client, resultados);
        //clientSearchIndex.put(client, 0); // Inicializa o índice para o cliente
        return numpages;
    }

    @Override
    public Integer searchUrls(ClientInterface client, String url) throws RemoteException {
        // Pesquisa num barrel
        List<String> resultados = new ArrayList<>();
        BarrelInterface barrel = getNextBarrel("client");

        if (!subscribedBarrels.isEmpty()) {
            resultados = barrel.searchUrl(url);
        } else {
            System.out.println("Nenhum Barrel disponível!");
        }

        int numpages = (int) Math.ceil(resultados.size() / 10.0);

        // Armazena os resultados no cache do cliente
        clientSearchCache.put(client, resultados);
        //clientSearchIndex.put(client, 0); // Inicializa o índice para o cliente

        return numpages;
    }

    @Override
    public synchronized List<String> sendtop10(ClientInterface client, Integer numPage) throws RemoteException {
        
        List<String> resultados = clientSearchCache.get(client);
        if (resultados == null) {
            System.out.println("Nenhuma pesquisa encontrada para este cliente.");
            return new ArrayList<>();
        }

        // Calcula índices corretos
        int startIndex = (numPage - 1) * 10;
        int nextIndex = Math.min(startIndex + 10, resultados.size());
        // Verifica se a página está fora do range
        if (startIndex >= resultados.size()) {
            List<String> emptyResult = new ArrayList<>();
            emptyResult.add("Não tem mais urls");
            return emptyResult;
        }
        
        // Obtém os resultados da página
        List<String> nextResults = new ArrayList<>(resultados.subList(startIndex, nextIndex));

        // Adiciona mensagem se for a última página
        if (nextIndex >= resultados.size()) {
            nextResults.add("Não tem mais urls");
        }

        return nextResults;
    }

    @Override
    public UrlMetadata getUrlMetadata(String url) throws RemoteException {
        if (!subscribedBarrels.isEmpty()) {
            try {
                BarrelInterface barrel = getNextBarrel("queue");
                if (barrel != null) {
                    return barrel.getUrlMetadata(url);
                }
            } catch (Exception e) {
                System.out.println("[Gateway] Erro ao tentar comunicar como barrel");
            }
        }
        return null;
    }

    @Override
    public int getReferenceCount(String url) throws RemoteException {
        BarrelInterface barrel = getNextBarrel("client");
        if (barrel != null) {
            return barrel.getReferenceCount(url);
        }
        return 0;
    }

    // --------------------------------------------------------
    // Queue
    // --------------------------------------------------------

    @Override
    public void sendQueue(BlockingDeque<String> queue, Map<String, Long> seenUrls) throws RemoteException {
        for(BarrelInterface barrel : subscribedBarrels) {
            barrel.receiveQueue(queue, seenUrls);
        }
    }

    @Override
    public BlockingDeque<String> getQueue() throws RemoteException {
        if (!subscribedBarrels.isEmpty()) {
            try {
                BarrelInterface barrel = getNextBarrel("queue");
                if (barrel != null) {
                    return barrel.getQueue();
                }
            } catch (Exception e) {
                System.out.println("[Gateway] Erro ao tentar comunicar como barrel");
            }
        }
        return null;
    }

    @Override
    public Map<String, Long> getSeenUrls() throws RemoteException {
        if (!subscribedBarrels.isEmpty()) {
            try {
                BarrelInterface barrel = getNextBarrel("queue");
                if (barrel != null) {
                    return barrel.getSeenUrls();
                }
            } catch (Exception e) {
                System.out.println("[Gateway] Erro ao tentar comunicar como barrel");
            }
        }
        return null;
    }

    @Override
    public String putNewUrl(String url, String id) throws RemoteException {
        if (queue != null) {
            queue.putNew(url, id);
            return "URL metido na queue";
        } else {
            return "Queue não está conectada!";
        }
    }

    // --------------------------------------------------------
    // Hacker News
    // --------------------------------------------------------

    @Override
    public void putHackerNews(String url, QueueInterface queue) throws RemoteException {
        subscribedRobots.get(0).extractInformationURL(url, queue);
    }

    // --------------------------------------------------------
    // Subscrever / Desinscrever
    // --------------------------------------------------------

    @Override
    public synchronized void reliableMulticast(BarrelInterface barrel)throws RemoteException {
        BarrelInterface barrelGiver = getNextBarrel("barrel");
        
        subscribedBarrels.add(barrel);
    }

    @Override
    public synchronized void subscribeBarrels(BarrelInterface barrel) throws RemoteException {
        System.out.println("[Gateway] Novo Barrel conectado!");

        // Notifica todos os robots que um Barrel está disponível
        if (subscribedBarrels.isEmpty()) {
            for (RobotInterface robot : subscribedRobots) {
                try {
                    robot.notifyrobot(); // Acorda os robots que estão em wait()
                } catch (RemoteException e) {
                    System.out.println("[Gateway] Erro ao notificar robot: " + e.getMessage());
                }
            }
        }

        subscribedBarrels.add(barrel);
        
        if (subscribedBarrels.size() > 1) {
            new Thread(() -> {
                try {
                    System.out.println("[Gateway] Iniciando sincronização assíncrona do novo barrel...");
                    
                    // Busca o primeiro barrel (não o recém-adicionado)
                    BarrelInterface referenceBarrel = subscribedBarrels.get(0);

                    Map<String, HashSet<String>> indexSnapshot = referenceBarrel.getIndexSnapshot();
                    Map<String, Set<String>> urlReferences = referenceBarrel.getUrlsReferencesSnapshot();
                    Map<String, UrlMetadata> urlMetadata = referenceBarrel.getUrlMetadataSnapshot();
                    Map<String, Set<String>> stopwords = referenceBarrel.getStopwordsByLangSnapshot();
                    Map<String, List<String>> urlWordCounts = referenceBarrel.getUrlWordCountsSnapshot();

                    barrel.syncIndex(indexSnapshot);
                    barrel.syncUrlReferences(urlReferences);
                    barrel.syncUrlMetadata(urlMetadata);
                    barrel.syncStopWords(stopwords);
                    barrel.syncUrlWordCounts(urlWordCounts);

                    System.out.println("[Gateway] Índice replicado para o novo barrel (" + indexSnapshot.size() + " palavras).");
                } catch (RemoteException e) {
                    System.out.println("[Gateway] Erro ao sincronizar índice para novo barrel: " + e.getMessage());
                    // Remove o barrel se a sincronização falhar
                    synchronized (Gateway.this) {
                        subscribedBarrels.remove(barrel);
                        for (ClientInterface cliente : subscribedClients) {
                            try {
                                cliente.deleteBarrel(barrel.getBarrelName());
                            } catch (RemoteException ex) {
                                System.out.println("[Gateway] Cliente inativo detectado, removendo...");
                                subscribedClients.remove(cliente);
                            }
                        }
                    }
                }
            }).start(); // Executa em paralelo
        } else {
            System.out.println("[Gateway] Primeiro barrel — não há índice para copiar ainda.");
        }

    }

    @Override
    public List<BarrelInterface> getsubscribedBarrel() throws RemoteException {
        return subscribedBarrels;
    }

    @Override
    public void subscribeClients(ClientInterface client) throws RemoteException {
        System.out.println("[Gateway] New Client Connection");
        this.subscribedClients.add(client);
    }

    @Override
    public void subscribeRobots(RobotInterface robot) throws RemoteException {
        System.out.println("[Gateway] New Robot Connection");
        this.subscribedRobots.add(robot);
    }

    @Override
    public void subscribeQueue(QueueInterface queue) throws RemoteException {
        System.out.println("[Gateway] New Queue Connection");
        this.queue = queue;
        for (RobotInterface robot : subscribedRobots) {
            robot.notifyrobot();
        }
    }

    @Override
    public void unsubscribeQueue(QueueInterface queue) throws RemoteException {
        this.queue = null;
    }

    @Override
    public void unsubscribeBarrel(BarrelInterface barrel, String barrelName) throws RemoteException {
        subscribedBarrels.remove(barrel);
        System.out.println("[Gateway] Barrel " + barrelName + " disconnected.");
        for (ClientInterface cliente : subscribedClients) {
            try {
                cliente.deleteBarrel(barrelName);
            } catch (RemoteException e) {
                System.out.println("[Gateway] Cliente inativo detectado, removendo...");
                subscribedClients.remove(cliente);
            }
        }
    }

    @Override
    public void unsubscribeClient(ClientInterface client) throws RemoteException {
        subscribedClients.remove(client);
    }

    @Override
    public void unsubscribeRobot(RobotInterface robot) throws RemoteException {
        subscribedRobots.remove(robot);
    }
} 
