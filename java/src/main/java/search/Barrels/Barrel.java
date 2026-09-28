package search.Barrels;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

import search.Gateway.GatewayInterface;
import search.Properties.ConfigProperties;

public class Barrel extends UnicastRemoteObject implements BarrelInterface {

    // Estrutura para representar uma mensagem única
    private static class Message {
        String type;      // "INDEX", "URL_REF", "WORD_COUNT", "METADATA"
        String url;
        Object data;      // Dados da mensagem (pode ser List<String>, Map, etc.)
        long timestamp;   // Para identificar mensagem única
        
        public Message(String type, String url, Object data) {
            this.type = type;
            this.url = url;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Message)) return false;
            Message m = (Message) o;
            return type.equals(m.type) && 
                   url.equals(m.url) && 
                   timestamp == m.timestamp;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(type, url, timestamp);
        }
    }

    // -------------------------------------------------------------------------------------
    // Variaveis para funcionamento do programa
    // -------------------------------------------------------------------------------------

    private ConcurrentHashMap<String, HashSet<String>> index = new ConcurrentHashMap<>();  // HASHMAP
    private Map<String, Set<String>> urlReferences = new ConcurrentHashMap<>();            // URL LIST
    private String barrelName; // Nome do barrel
    private BlockingDeque<String> queue = new LinkedBlockingDeque<>(); //QUEUE
    private Map<String, Long> seenUrls = new HashMap<>(); // URL -> timestamp
    private Map<String, List<String>> urlWordCounts = new ConcurrentHashMap<>(); //hashmap para contar palavras por url
    
    private Map<String, Set<String>> stopwordsByLang = new ConcurrentHashMap<>(); // Lang -> Set<Stopwords>
    private Map<String, String> urlLanguages = new ConcurrentHashMap<>();

    private Map<String, UrlMetadata> urlMetadata = new ConcurrentHashMap<>(); // Informações de cada url
    private Set<Message> receivedMessages = ConcurrentHashMap.newKeySet(); // Conjunto de mensagens já recebidas (para evitar duplicados)

    private long numSearches = 0; // Número total de pesquisas
    private double timeSearches = 0; // Tempo total gasto em pesquisas
    private double meanTime = 0; // Tempo médio por pesquisa

    private int size = 0; // Tamanho do índice

    private GatewayInterface gateway;

    public Barrel() throws RemoteException {
        super();
    }

    private void connectToGateway(ConfigProperties config) throws Exception {

        while (gateway == null) {
            try {
                gateway = (GatewayInterface) LocateRegistry.getRegistry(config.get("gateway.host"), config.getInt("gateway.port")).lookup(config.get("gateway.name"));
                System.out.println("[Barrel] Conectado ao Gateway com sucesso!");
            } catch (Exception e) {
                System.out.println("[Barrel] Gateway não disponível, tentando novamente em 5 segundos...");
                Thread.sleep(5000);
            }
        }
    }
    
    public static void main(String args[]) {

        //server.putNew("https://pt.wikipedia.org/wiki/Wikip%C3%A9dia:P%C3%A1gina_principal");

        try {
            
            ConfigProperties config = new ConfigProperties();

            // Lê a configuração base
            String baseName = config.get("barrel1.name"); // ex: "barrel1"
            int basePort = config.getInt("barrel1.port"); // ex: 8184
            String hostName = java.net.InetAddress.getLocalHost().getHostName();
            int serverPort = config.getInt("server.port");

            int i = 1; // começa no barrel1

            while (true) { // ou substitui por for se quiseres um limite

                try {
                    Barrel server = new Barrel(); // objeto remoto

                    // Gera nome e porta dinamicamente
                    String barrelName;
                    int port;

                    String nameKey = "barrel" + i + ".name";
                    String portKey = "barrel" + i + ".port";

                    if (config.get(nameKey) != null && config.get(portKey) != null) {
                        barrelName = config.get(nameKey) + "_" + hostName; // ex: barrel1_PC01
                        port = config.getInt(portKey);
                    } else {
                        // se acabarem os barrelX do config, cria novos automaticamente
                        barrelName = baseName + "_" + i + "_" + hostName;
                        port = basePort + (i - 1); // incrementa a partir da base
                    }

                    if (port == serverPort) {
                        System.out.println("[Barrel] A porta 8443 será ignorada. Tentando a próxima...");
                        i++;
                        continue; // Volta ao início do while
                    }

                    System.out.println("[Barrel] A tentar criar " + barrelName + " na porta " + port);

                    Registry registry = LocateRegistry.createRegistry(port);
                    registry.bind(barrelName, server);
                    server.barrelName = barrelName;

                    server.connectToGateway(config);

                    String stateFile = server.barrelName + ".obj";

                    // --- Carregar estado (mantém igual ao teu) ---
                    if (open(stateFile)) {
                        server.loadState(stateFile);
                    }

                    server.gateway.subscribeBarrels(server);

                    // --- Lock para manter o processo ativo ---
                    Object lock = new Object();

                    // --- Shutdown hook (mantém igual ao teu) ---
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        System.out.println("[Barrel] Shutdown hook a correr, a limpar recursos...");
                        try {
                            server.saveState(stateFile);
                            try { server.gateway.unsubscribeBarrel(server, barrelName); } catch (Exception ignore) {}
                            try { UnicastRemoteObject.unexportObject(server, true); } catch (Exception ignore) {}
                            try { UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignore) {}
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {
                            synchronized (lock) {
                                lock.notifyAll();
                            }
                        }
                    }));

                    server.gateway.sendStatisticsBarrel(server);

                    // --- Fica à espera do Ctrl+C ---
                    synchronized (lock) {
                        lock.wait();
                    }

                    break; // se quiseres apenas 1 barrel por execução

                } catch (java.rmi.server.ExportException e) {
                    System.out.println("[Barrel] Porta ocupada (" + e.getMessage() + "), tentando próxima...");
                    i++; // tenta o próximo barrel (nova porta e nome)
                } catch (java.rmi.AlreadyBoundException e) {
                    System.out.println("[Barrel] Nome já em uso, tentando próximo...");
                    i++;
                } catch (Exception e) {
                    System.out.println("[Barrel] Erro ao criar barrel " + i + ": " + e.getMessage());
                    e.printStackTrace();
                    i++;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    // -------------------------------------------------------------------------------------
    // Função para armazanar todo o barrel num ficheiro de objetos
    // -------------------------------------------------------------------------------------

    private static boolean open(String filename) {
        File file = new File(filename);
        return file.exists();
    }

    public synchronized void saveState(String filename) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            // Cria um objeto que contém todos os estados
            ConcurrentHashMap<String, HashSet<String>> indexCopy = new ConcurrentHashMap<>();
            synchronized(index) {
                for (Map.Entry<String, HashSet<String>> entry : index.entrySet()) {
                    indexCopy.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
            }
            
            Map<String, Set<String>> urlRefCopy;
            synchronized(urlReferences) {
                urlRefCopy = new HashMap<>();
                for (Map.Entry<String, Set<String>> entry : urlReferences.entrySet()) {
                    urlRefCopy.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
            }
            
            BlockingDeque<String> queueCopy;
            synchronized(queue) {
                queueCopy = new LinkedBlockingDeque<>(queue);
            }
            
            Map<String, Long> seenUrlsCopy;
            synchronized(seenUrls) {
                seenUrlsCopy = new HashMap<>(seenUrls);
            }
            
            Map<String, List<String>> wordCountsCopy;
            synchronized(urlWordCounts) {
                wordCountsCopy = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : urlWordCounts.entrySet()) {
                    wordCountsCopy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
            
            Map<String, Set<String>> stopwordsCopy;
            synchronized(stopwordsByLang) {
                stopwordsCopy = new HashMap<>();
                for (Map.Entry<String, Set<String>> entry : stopwordsByLang.entrySet()) {
                    stopwordsCopy.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
            }
            
            Map<String, String> urlLanguagesCopy;
            synchronized(urlLanguages) {
                urlLanguagesCopy = new HashMap<>(urlLanguages);
            }
            
            Map<String, UrlMetadata> metadataCopy;
            synchronized(urlMetadata) {
                metadataCopy = new HashMap<>(urlMetadata);
            }
        
            
            oos.writeObject(indexCopy);
            oos.writeObject(urlRefCopy);
            oos.writeObject(queueCopy);
            oos.writeObject(seenUrlsCopy);
            oos.writeObject(wordCountsCopy);
            oos.writeObject(stopwordsCopy);
            oos.writeObject(urlLanguagesCopy);
            oos.writeObject(timeSearches);
            oos.writeObject(numSearches);
            oos.writeObject(meanTime);
            oos.writeObject(size);
            oos.writeObject(metadataCopy);
            
            System.out.println("Estado do Barrel salvo em: " + filename);
        }
    }

    public synchronized void loadState(String filename) throws IOException, ClassNotFoundException {

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            this.index = (ConcurrentHashMap<String, HashSet<String>>) ois.readObject();
            this.urlReferences = (Map<String, Set<String>>) ois.readObject();
            this.queue = (BlockingDeque<String>) ois.readObject();
            this.seenUrls = (Map<String, Long>) ois.readObject();
            this.urlWordCounts = (Map<String, List<String>>) ois.readObject();
            this.stopwordsByLang = (Map<String, Set<String>>) ois.readObject();
            this.urlLanguages = (Map<String, String>) ois.readObject();
            this.timeSearches = (double) ois.readObject();
            this.numSearches = (long) ois.readObject();
            this.meanTime = (double) ois.readObject();
            this.size = (int) ois.readObject();
            this.urlMetadata = (Map<String, UrlMetadata>) ois.readObject();
            System.out.println("Estado do Barrel carregado de: " + filename);
        } catch (java.io.EOFException e) {
            System.err.println("[Barrel] AVISO: Ficheiro de estado corrompido ou incompleto: " + filename);
            System.err.println("[Barrel] A começar com estado limpo...");
            // Apaga o ficheiro corrompido
            new File(filename).delete();
            // Inicializa com valores padrão (já estão inicializados nas variáveis de instância)
        }
    }

    // -------------------------------------------------------------------------------------
    // Zona do código onde implementamos o Reliable Multicast
    // -------------------------------------------------------------------------------------

    private void reliableMulticast(Message msg) {
        try {
            List<BarrelInterface> barrels = gateway.getsubscribedBarrel();
            
            for (BarrelInterface barrel : barrels) {
                
                new Thread(() -> {
                    try {
                        switch (msg.type) {
                            case "INDEX":
                                barrel.receiveIndexUpdate((List<String>) msg.data, msg.url, msg.timestamp);
                                break;
                            case "URL_REF":
                                barrel.receiveUrlReferenceUpdate((List<String>) msg.data, msg.url, msg.timestamp);
                                break;
                            case "WORD_COUNT":
                                barrel.receiveWordCountUpdate((Map<String, Integer>) msg.data, msg.url, msg.timestamp);
                                break;
                            case "METADATA":
                                barrel.receiveMetadataUpdate((UrlMetadata) msg.data, msg.timestamp);
                                break;
                            case "LANG":
                                barrel.receiveUrlLanguageUpdate((String) msg.data, msg.url, msg.timestamp); 
                                break;                            
                        }
                    } catch (RemoteException e) {
                        System.out.println("[Barrel] Erro ao enviar para barrel (async): " + e.getMessage());
                    }
                }).start();
            }
        } catch (RemoteException e) {
            System.out.println("[Barrel] Erro ao obter barrels: " + e.getMessage());
        }
    }

    @Override
    public void receiveIndexUpdate(List<String> words, String url, long timestamp) throws RemoteException {

        Message msg = new Message("INDEX", url, words);
        msg.timestamp = timestamp;
        
        // Se já recebeu, ignora
        if (receivedMessages.contains(msg)) {
            return;
        }
        
        // Marca como recebida
        receivedMessages.add(msg);
        
        // Reencaminha para outros barrels (Agreement)
        reliableMulticast(msg);
        
        // Processa localmente (R-deliver)
        for (String word : words) {
            addToIndex(word, url);
        }
    }

    @Override
    public void receiveUrlReferenceUpdate(List<String> referencedUrls, String scannedUrl, long timestamp) throws RemoteException {
        Message msg = new Message("URL_REF", scannedUrl, referencedUrls);
        msg.timestamp = timestamp;
        
        if (receivedMessages.contains(msg)) {
            return;
        }
        
        receivedMessages.add(msg);
        reliableMulticast(msg);
        
        // Processa localmente
        for (String referencedUrl : referencedUrls) {
            urlReferences.putIfAbsent(referencedUrl, new HashSet<>());
            urlReferences.get(referencedUrl).add(scannedUrl);
        }
    }

    @Override
    public void receiveWordCountUpdate(Map<String, Integer> wordCounts, String url, long timestamp) throws RemoteException {
        Message msg = new Message("WORD_COUNT", url, wordCounts);
        msg.timestamp = timestamp;
        
        if (receivedMessages.contains(msg)) {
            return;
        }
        
        receivedMessages.add(msg);
        reliableMulticast(msg);
        
        // Processa localmente
        addWordCountsLocal(wordCounts, url);
    }

    @Override
    public void receiveMetadataUpdate(UrlMetadata metadata, long timestamp) throws RemoteException {
        Message msg = new Message("METADATA", metadata.getUrl(), metadata);
        msg.timestamp = timestamp;
        
        if (receivedMessages.contains(msg)) {
            return;
        }
        
        receivedMessages.add(msg);
        reliableMulticast(msg);
        
        // Processa localmente
        urlMetadata.put(metadata.getUrl(), metadata);
    }

    // -------------------------------------------------------------------------------------
    // Zona do código onde adiciona para lista de URLS e as suas referencias 
    // -------------------------------------------------------------------------------------

    @Override
    public void sendUrlInfo(List<String> words, String url, List<String> referencedUrls, String title, String snippet, String language) throws RemoteException{
        System.out.println("[Barrel] A adicionar informação do URL: " + url);
        addUrlLanguage(url, language);
        WordsToAddToIndex(words, url);
        addUrlMetadata(url, title, snippet);
        addUrlReference(url, referencedUrls);
        countWords(words,url);
        System.out.println("[Barrel] Informação do URL adicionada: " + url);
    }

    public void addUrlMetadata(String url, String title, String snippet) throws RemoteException {
        UrlMetadata metadata = new UrlMetadata(url, title, snippet);
        Message msg = new Message("METADATA", url, metadata);
        
        // Se já recebeu esta mensagem, ignora (Integrity)
        if (receivedMessages.contains(msg)) {
            return;
        }
        
        // Marca como recebida
        receivedMessages.add(msg);
        
        // B-multicast para outros barrels (Agreement)
        reliableMulticast(msg);
        
        // R-deliver: Processa a mensagem localmente (Validity)
        synchronized (urlMetadata) {
            urlMetadata.put(url, metadata);
        }
    }

    public void addUrlReference(String scannedUrl, List<String> referencedUrls) throws RemoteException {
        
        Message msg = new Message("URL_REF", scannedUrl, referencedUrls);
    
        // Se já recebeu esta mensagem, ignora (Integrity)
        if (receivedMessages.contains(msg)) {
            return;
        }
        
        // Marca como recebida
        receivedMessages.add(msg);
        
        // B-multicast para outros barrels (Agreement)
        reliableMulticast(msg);
        
        // R-deliver: Processa a mensagem localmente (Validity)
        addUrlReferenceLocal(scannedUrl, referencedUrls);
    }

    // Método local sem multicast
    private void addUrlReferenceLocal(String scannedUrl, List<String> referencedUrls) {
        synchronized(urlReferences) {
            for (String referencedUrl : referencedUrls) {
                // Se o URL referenciado ainda não existe, cria um novo Set
                urlReferences.putIfAbsent(referencedUrl, new HashSet<>());
                
                // Adiciona o URL que está a ser escaneado ao Set de referenciadores
                urlReferences.get(referencedUrl).add(scannedUrl);
            }
        }
    }

    @Override
    public void WordsToAddToIndex(List<String> words, String url) throws java.rmi.RemoteException {
        Message msg = new Message("INDEX", url, words);
    
        // Se já recebeu esta mensagem, ignora (Integrity)
        if (receivedMessages.contains(msg)) {
            return;
        }
        
        // Marca como recebida
        receivedMessages.add(msg);
        
        // B-multicast para outros barrels (Agreement)
        reliableMulticast(msg);
        
        // R-deliver: Processa a mensagem localmente (Validity)
        for (String word : words) {
            addToIndex(word, url);
        }
    }
    
    public void addToIndex(String word, String url) throws java.rmi.RemoteException {

        synchronized(index) {
            // Verifica se a palavra já existe e se o URL já está associado a ela
            if (index.containsKey(word) && index.get(word).contains(url)) {
                // Palavra e URL já existem, não faz nada
            
                return;
            }

            if(index.containsKey(word)){
                
                HashSet<String> urls = index.get(word);
                urls.add(url);
                index.put(word, urls);
            }else {
            
                HashSet<String> urlsNovos = new HashSet<>();
                urlsNovos.add(url);
                index.put(word, urlsNovos);
            }

            if (size != index.size()) {
                size = index.size();
                gateway.sendSize(size, barrelName);
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Zona do código onde integra dados vindos de outros barrels
    // -------------------------------------------------------------------------------------

    // recebe e integra índice vindo de outro barrel
    @Override
    public void syncIndex(Map<String, HashSet<String>> otherIndex) throws RemoteException {
        System.out.println("[Barrel] Índice original (" + index.size() + " entradas).");
        //Percorre cada palavra do índice recebido
        for (Map.Entry<String, HashSet<String>> entry : otherIndex.entrySet()) {
            String word = entry.getKey();
            for (String url : entry.getValue()) {
                addToIndex(word, url);
            }
        }
        System.out.println("[Barrel] Índice sincronizado com outro barrel (" + otherIndex.size() + " entradas).");

        if (size != index.size()) {
            size = index.size();
            gateway.sendSize(size, barrelName);
        }
    }

    @Override
    public void syncUrlReferences(Map<String, Set<String>> otherReferences) throws RemoteException {

        synchronized (urlReferences) {
            for (Map.Entry<String, Set<String>> entry : otherReferences.entrySet()) {
                String url = entry.getKey();
                Set<String> referencers = entry.getValue();

                if (urlReferences.containsKey(url)) {
                    // URL já existe, adiciona apenas os urls que faltam
                    Set<String> existingReferencers = urlReferences.get(url);
                    for (String referencer : referencers) {
                        if (!existingReferencers.contains(referencer)) {
                            existingReferencers.add(referencer);
                        }
                    }
                } else {
                    // URL não existe, cria nova entrada com todos os urls
                    urlReferences.put(url, new HashSet<>(referencers));
                }
            }
        }
    }

    @Override
    public void syncUrlWordCounts(Map<String, List<String>> otherWordCounts) throws RemoteException {

        synchronized (urlWordCounts) {
            for (Map.Entry<String, List<String>> entry : otherWordCounts.entrySet()) {
                String url = entry.getKey();
                List<String> words = entry.getValue();

                if (urlWordCounts.containsKey(url)) {
                    // URL já existe, adiciona apenas as palavras que faltam
                    List<String> existingWords = urlWordCounts.get(url);
                    for (String word : words) {
                        if (!existingWords.contains(word)) {
                            existingWords.add(word);
                        }
                    }
                } else {
                    // URL não existe, cria nova entrada com todas as palavras
                    urlWordCounts.put(url, new ArrayList<>(words));
                }
            }
        }
    }

@Override
    public void syncStopWords(Map<String, Set<String>> otherStopwords) throws RemoteException {
        synchronized (stopwordsByLang) {
            for (Map.Entry<String, Set<String>> entry : otherStopwords.entrySet()) {
                String lang = entry.getKey();
                Set<String> words = entry.getValue();
                
                // Merge dos sets para cada lingua
                stopwordsByLang.computeIfAbsent(lang, k -> new ConcurrentHashMap().newKeySet()).addAll(words);
            }
        }
    }


    @Override
    public void syncUrlLanguages(Map<String, String> otherLanguages) throws RemoteException {
        synchronized (urlLanguages) {
            // Adiciona apenas as entradas que não existem
            for (Map.Entry<String, String> entry : otherLanguages.entrySet()) {
                urlLanguages.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    // --- Atualizar/Adicionar Snapshots ---
    
    @Override // Nota: O nome na interface mudou de getStopwordsSnapshot para getStopwordsByLangSnapshot
    public Map<String, Set<String>> getStopwordsByLangSnapshot() throws RemoteException {
        synchronized (stopwordsByLang) {
            // Criação de cópia profunda para evitar problemas de concorrência ou serialização
            Map<String, Set<String>> copy = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : stopwordsByLang.entrySet()) {
                copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return copy;
        }
    }

    @Override
    public Map<String, String> getUrlLanguagesSnapshot() throws RemoteException {
        synchronized (urlLanguages) {
            return new HashMap<>(urlLanguages);
        }
    }

    @Override
    public void syncUrlMetadata(Map<String, UrlMetadata> otherMetadata) throws RemoteException {

        synchronized (urlMetadata) {
            for (Map.Entry<String, UrlMetadata> entry : otherMetadata.entrySet()) {
                String url = entry.getKey();
                UrlMetadata metadata = entry.getValue();

                if (!urlMetadata.containsKey(url)) {
                    urlMetadata.put(url, metadata);
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Zona do código onde envia snapshots dos dados atuais
    // -------------------------------------------------------------------------------------

    @Override
    public UrlMetadata getUrlMetadata(String url) throws RemoteException {
        synchronized (urlMetadata) {
            return urlMetadata.get(url);
        }
    }

    // envia cópia do hashMap com as palavras e os urls atual
    @Override
    public Map<String, HashSet<String>> getIndexSnapshot() throws RemoteException {
        synchronized (index) {
            return new HashMap<>(index); // cópia defensiva
        }
    }

    @Override
    public Map<String, Set<String>> getUrlsReferencesSnapshot() throws RemoteException {
        synchronized (urlReferences) {
            return new HashMap<>(urlReferences); // cópia defensiva
        }
    }

    @Override
    public Map<String, UrlMetadata> getUrlMetadataSnapshot() throws RemoteException {
        synchronized (urlMetadata) {
            return new HashMap<>(urlMetadata); // cópia defensiva
        }
    }

    @Override
    public Map<String, List<String>> getUrlWordCountsSnapshot() throws RemoteException {
        synchronized (urlWordCounts) {
            return new HashMap<>(urlWordCounts); // cópia defensiva
        }
    }
    
    // -------------------------------------------------------------------------------------
    // Zona do código onde pesquisa as palavras e retorna uma lista de strings ordenada 
    // -------------------------------------------------------------------------------------

    private String cleanWord(String word) {
        // Remove todos os caracteres que não sejam letras (incluindo acentos) ou números
        String cleaned = word.replaceAll("[^a-zA-ZÀ-ÿ0-9\\s]", "");
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
    public List<String> searchWord(String phrase) throws java.rmi.RemoteException {
        long startNanos = System.nanoTime(); 
        long endNanos;
        numSearches++;
        System.out.println("Searching for: " + phrase);

        String cleanPhrase = cleanWord(phrase);
        
        String lang = detectLanguage(cleanPhrase);

        String[] words = cleanPhrase.toLowerCase().split("\\s+"); // divide a frase em palavras
        Set<String> resultSet = null; // vai conter a interseção

        for (String word : words) {
            if (this.isStopword(word, lang)) continue;
            HashSet<String> urls = index.get(word);

            if (urls != null) {

                // primeira palavra — inicializa o conjunto com os URLs dela
                if (resultSet == null) {
                    resultSet = new HashSet<>(urls);
                }
                // próximas palavras — mantém apenas os URLs comuns
                else {
                    resultSet.retainAll(urls);
                }
            } else {
                // se uma palavra não estiver no índice, o resultado será vazio
                System.out.println("No results for \"" + word + "\"");
                endNanos = System.currentTimeMillis();
                return new ArrayList<>();
            }
        }

        if (resultSet == null) {
            endNanos = System.nanoTime();
            // Nenhuma conversão se não houver resultados
            return new ArrayList<>(); 
        }

        List<String> results = sortUrlsByReferences(new ArrayList<>(resultSet));

        endNanos = System.nanoTime();
        
        // Calcula o tempo da pesquisa em nanosegundos
        long durationNanos = endNanos - startNanos;

        // 2. Acumula o tempo em nanosegundos (double para precisão)
        timeSearches += durationNanos;

        // 3. Verifica e atualiza o tempo médio
        if (numSearches > 0) {

            double newMeanNanos = timeSearches / numSearches;
            
            // 4. Converte a média de nanosegundos para décimas de segundo (ds)
            double newMeanDeciseconds = newMeanNanos / 100_000_000.0;
            
            if (newMeanDeciseconds != meanTime) {
                meanTime = newMeanDeciseconds;
                gateway.sendMeanTime(meanTime, barrelName); 
            }
        }
        
        System.out.println("------------------------------------------------------------\n" + timeSearches + "\n------------------------------------------------------------\n");
        
        return results;
    }

    @Override
    public List<String> searchUrl(String url) throws java.rmi.RemoteException {

        System.out.println("Searching for: " + url);
       

        Set<String> resultSet = null; // vai conter a interseção


        Set<String> urls = urlReferences.get(url);

        if (urls != null) {
            resultSet = new HashSet<>(urls);
        } else {
            // se uma palavra não estiver no índice, o resultado será vazio
            System.out.println("No results for \"" + url + "\"");
            return new ArrayList<>();
        }

        if (resultSet == null) {
            return new ArrayList<>(); // nada encontrado
        }

        return sortUrlsByReferences(new ArrayList<>(resultSet));
    }

    // Função auxiliar para ordenar os URLs com base no tamanho da lista de referências
    private List<String> sortUrlsByReferences(List<String> urls) {
        urls.sort((url1, url2) -> {
            // Obtém o tamanho da lista de referências para cada URL
            int size1 = urlReferences.getOrDefault(url1, new HashSet<>()).size();
            int size2 = urlReferences.getOrDefault(url2, new HashSet<>()).size();

            // Ordena em ordem decrescente (mais referenciado primeiro)
            return Integer.compare(size2, size1);
        });
        int i = 0;
        for(String URL : urls){
            int size_url = urlReferences.getOrDefault(URL, new HashSet<>()).size();
            System.out.println("URL "+ i++ + " : " + size_url);
        }
        return urls;
    }

    // -------------------------------------------------------------------------------------
    // Zona do código onde gerencia a Queue e SeenUrls
    // -------------------------------------------------------------------------------------
    
    @Override
    public void receiveQueue(BlockingDeque<String> queue, Map<String, Long> seenUrls) throws RemoteException {
        this.queue = queue;
        this.seenUrls = seenUrls;
    }

    @Override
    public BlockingDeque<String> getQueue() throws RemoteException {
        return this.queue;
    }

    @Override
    public Map<String, Long> getSeenUrls() throws RemoteException {
        return this.seenUrls;
    }

    @Override
    public int getReferenceCount(String url) throws RemoteException {
        synchronized (urlReferences) {
            Set<String> refs = urlReferences.get(url);
            if (refs != null) {
                System.out.println(url + ": " + refs.size());
                return refs.size();
            } else {
                System.out.println(url + ": 0 (URL não encontrado)");
                return 0;
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Zona do código onde gerencia as estatísticas do barrel
    // -------------------------------------------------------------------------------------

    @Override
    public double getMeanTime() throws RemoteException {
        return this.meanTime;
    }

    @Override
    public int getSize() throws RemoteException {
        return this.size;
    }

    @Override
    public String getBarrelName() throws RemoteException {
        return this.barrelName;
    }

    // -------------------------------------------------------------------------------------
    // Zona do código onde implementamos as Stopwords
    // -------------------------------------------------------------------------------------

    private static final double STOPWORD_THRESHOLD = 0.25; // 25% dos documentos precisam ter a palavra
    private static final double OUTLIER_K = 2; // Fator multiplicativo para o IQR (mais conservador)
    private static final int MIN_WORD_FREQUENCY = 5; // Frequência mínima para considerar outlier

    @Override
    public void receiveUrlLanguageUpdate(String language, String url, long timestamp) throws RemoteException {
        Message msg = new Message("LANG", url, language);
        msg.timestamp = timestamp;

        if (receivedMessages.contains(msg)) return;
        receivedMessages.add(msg);
        reliableMulticast(msg);

        // Processa localmente
        urlLanguages.put(url, language);
    }

    public void addUrlLanguage(String url, String language) throws RemoteException {
        // Criar uma mensagem simples. Como o Message aceita Object data, passamos a String language
        // Nota: Podes precisar de adicionar "LANG" ao teu switch no reliableMulticast se quiseres ser rigoroso,
        // mas aqui vou assumir que usas um tipo novo ou reutilizas METADATA se modificares a classe.
        // Para ser limpo, vamos adicionar um case novo no reliableMulticast:
        
        Message msg = new Message("LANG", url, language);

        if (receivedMessages.contains(msg)) return;
        receivedMessages.add(msg);
        reliableMulticast(msg);
        
        // Local
        urlLanguages.put(url, language);
    }

    // Método local sem multicast (para usar no reliable multicast)
    private void addWordCountsLocal(Map<String, Integer> wordCounts, String url) {
        List<String> outliers = new ArrayList<>();

        if (wordCounts == null || wordCounts.isEmpty()) {
            urlWordCounts.put(url, outliers);
            return;
        }
        
        // Filtra palavras com frequência mínima antes de calcular quartis
        List<Integer> frequencias = wordCounts.values().stream()
            .filter(count -> count >= MIN_WORD_FREQUENCY)
            .sorted()
            .collect(Collectors.toList());
        
        if (frequencias.isEmpty()) {
            urlWordCounts.put(url, outliers);
            return;
        }

        // Calcula média das frequências para referência
        double mediaFreq = frequencias.stream().mapToInt(Integer::intValue).average().getAsDouble();
        
        // Calcula os quartis
        double Q1 = calcularPercentil(frequencias.stream().mapToInt(Integer::intValue).toArray(), 25);
        double Q3 = calcularPercentil(frequencias.stream().mapToInt(Integer::intValue).toArray(), 75);
        double IQR = Q3 - Q1;
        
        // Define o limite superior usando o fator K
        double limiteSuperior = Q3 + (OUTLIER_K * IQR);
        
        // Filtra palavras realmente extremas
        for (Map.Entry<String, Integer> entry : wordCounts.entrySet()) {
            int freq = entry.getValue();
            if (freq > limiteSuperior && freq >= MIN_WORD_FREQUENCY) {
                outliers.add(entry.getKey());
            }
        }
        
        urlWordCounts.put(url, outliers);

        if (!outliers.isEmpty()) {
            findStopwords();
        }
    }

    private void countWords(List<String> words, String url) throws RemoteException {
        Map<String, Integer> wordCount = new HashMap<>();

        for (String word : words) {
            if (word == null || word.isEmpty()) continue;
            // ajusta aqui se quiseres ignorar palavras curtas: e.g. if (word.length() < 1) continue;
            wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
        }
        addWordCounts(wordCount,url);
    }

    public void addWordCounts(Map<String, Integer> wordCounts, String url) throws RemoteException {
        Message msg = new Message("WORD_COUNT", url, wordCounts);
    
        // Se já recebeu esta mensagem, ignora (Integrity)
        if (receivedMessages.contains(msg)) {
            return;
        }
        
        // Marca como recebida
        receivedMessages.add(msg);
        
        // B-multicast para outros barrels (Agreement)
        reliableMulticast(msg);
        
        // R-deliver: Processa a mensagem localmente (Validity)
        addWordCountsLocal(wordCounts, url);
    }
    
    private double calcularPercentil(int[] dados, double percentil) {
        if (dados.length == 0) {
            return 0.0;
        }
        
        int n = dados.length;
        double posicao = (percentil / 100.0) * (n - 1);
        int indiceInferior = (int) Math.floor(posicao);
        int indiceSuperior = (int) Math.ceil(posicao);
        
        if (indiceInferior == indiceSuperior) {
            return dados[indiceInferior];
        }
        
        // Interpolação linear entre os dois pontos mais próximos
        double valorInferior = dados[indiceInferior];
        double valorSuperior = dados[indiceSuperior];
        double fracao = posicao - indiceInferior;
        
        return valorInferior + fracao * (valorSuperior - valorInferior);
    }

    public void findStopwords() {

        if (urlWordCounts == null || urlWordCounts.isEmpty()) {
            return;
        }

        // Mapa temporário para calcular as novas stopwords
        Map<String, Set<String>> calculatedStopwordsByLang = new HashMap<>();

        // 1. Agrupar URLs por idioma usando o mapa urlLanguages
        // Map<LangCode, List<URL>>
        Map<String, List<String>> urlsByLanguage = urlLanguages.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue,
                     Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

        // 2. Iterar sobre cada idioma
        for (Map.Entry<String, List<String>> entry : urlsByLanguage.entrySet()) {
            String lang = entry.getKey();
            List<String> urlsForThisLang = entry.getValue();

            int totalDocsLang = urlsForThisLang.size();
            
            // Se tivermos poucos documentos de uma língua, se calhar não calculamos ainda
            if (totalDocsLang < 2) continue; 

            int limiar = (int) Math.ceil(totalDocsLang * STOPWORD_THRESHOLD);

            Map<String, Integer> contagemPalavras = new HashMap<>();

            // 3. Contar DF (Document Frequency) APENAS nos URLs desta língua
            for (String url : urlsForThisLang) {
                List<String> outliers = urlWordCounts.get(url);
                if (outliers != null) {
                    Set<String> palavrasUnicas = new HashSet<>(outliers);
                    for (String palavra : palavrasUnicas) {
                        contagemPalavras.merge(palavra, 1, Integer::sum);
                    }
                }
            }

            // 4. Determinar stopwords para esta língua
            Set<String> newStopwords = new HashSet<>();
            for (Map.Entry<String, Integer> wordEntry : contagemPalavras.entrySet()) {
                if (wordEntry.getValue() >= limiar) {
                    newStopwords.add(wordEntry.getKey());
                }
            }
            
            calculatedStopwordsByLang.put(lang, newStopwords);
        }

        // 5. Atualizar o mapa global (Thread-safe) e Loggar alterações
        synchronized (stopwordsByLang) {
            for (Map.Entry<String, Set<String>> entry : calculatedStopwordsByLang.entrySet()) {
                String lang = entry.getKey();
                Set<String> newSet = entry.getValue();
                
                Set<String> currentSet = stopwordsByLang.getOrDefault(lang, new HashSet<>());
                
                // Apenas para logging bonito
                Set<String> added = new HashSet<>(newSet);
                added.removeAll(currentSet);
                
                if (!added.isEmpty()) {
                    System.out.println("[StopWords][" + lang + "] Novas detetadas: " + added);
                }
                
                stopwordsByLang.put(lang, newSet);
                System.out.println("Linguagem: " + lang + "\nStop Words:" + newSet);
            }
        }
    }
    // Método para verificar se uma palavra é stopword
    public boolean isStopword(String palavra, String lang) throws RemoteException {
        Set<String> set = stopwordsByLang.get(lang);
        return set != null && set.contains(palavra);
    }

}