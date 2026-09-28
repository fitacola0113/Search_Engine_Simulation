package search.Downloaders;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import search.Barrels.BarrelInterface;
import search.Gateway.GatewayInterface;
import search.Properties.ConfigProperties;
import search.Queue.QueueInterface;


public class Robot extends UnicastRemoteObject implements RobotInterface {

    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_BLUE = "\u001B[32m";
    public static final String ANSI_WHITE = "\u001B[30m";
    public static final String ANSI_RESET = "\u001B[0m";
    private final Object lock = new Object();
    private GatewayInterface gateway;

    public Robot() throws java.rmi.RemoteException {
        super();
    }

    @Override
    public void notifyrobot() throws java.rmi.RemoteException {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public static Document isLoginPage(String url) {

        int count = 0;
        int status = 0;
        Document doc = null;

        while(count < 3){

            try {

                Connection.Response response = Jsoup.connect(url)
                        .ignoreHttpErrors(true)
                        .timeout(500)
                        .execute();

                status = response.statusCode();

                System.out.println("(" + status + ")" + ANSI_WHITE + url + ANSI_RESET);
                
                doc = Jsoup.connect(url).get();

                return doc;
                
            } catch (Exception e) {
                if (status >= 400 && status < 500) {
                    System.out.println(ANSI_RED + "[PageChecker] Página restrita (" + status + "): " + url + ANSI_RESET);
                    try{
                        Thread.sleep(1000);
                    }catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    count++;
                } else
                return doc; 
            }
        }
        System.out.println(ANSI_BLUE + "URL not accessible after Three Tries" + ANSI_RESET);
        return null;
    }

    private static List<String> extractReferencedUrls(Document doc, QueueInterface queue) throws RemoteException {
        List<String> referencedUrls = new ArrayList<>();
        Elements links = doc.select("a[href]"); // Seleciona todos os links no documento
        for (Element link : links) {
            String url = link.attr("abs:href");
            if (queue != null) {
                queue.putNew(url, "crawler"); // AaddUrlReferencediciona o URL à queue
            }
            referencedUrls.add(url);      // Adiciona o URL à lista
        }
        return referencedUrls;
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
    
    private void multicastToBarrels(GatewayInterface gateway, List<String> words, String url, List<String> referencedUrls, String title, String snippet, String language) {

        try {
            // Obtém a lista de barrels do Gateway
            List<BarrelInterface> barrels = gateway.getsubscribedBarrel();

            //Espera até que tenha algum barrel ativo
            while (barrels.isEmpty()) {
                synchronized (lock) {
                    System.out.println("[Robot] Aguardando Barrels disponíveis...");
                    lock.wait(); // Espera até ser notificado
                }
                barrels = gateway.getsubscribedBarrel(); // Tenta buscar novamente após ser notificado
            }

            // Itera sobre os barrels e envia os dados
            for (BarrelInterface barrel : barrels) {
                try {
                    // System.out.println("[Robot] Enviando dados para o Barrel: " + barrel.getBarrelName());
                    barrel.sendUrlInfo(words, url, referencedUrls, title, snippet,language);
                    // System.out.println("[Robot] Dados enviados com sucesso para o Barrel: " + barrel.getBarrelName());
                } catch (RemoteException e) {
                    System.out.println("[Robot] Erro ao comunicar com o Barrel: " + e.getMessage());
                }
            }
        } catch (RemoteException | InterruptedException e) { 
            System.out.println("[Robot] Erro ao obter barrels do Gateway: " + e.getMessage());
        }
    }

    private void connectToGateway(ConfigProperties config) throws Exception {

        while (gateway == null) {
            try {
                gateway = (GatewayInterface) LocateRegistry.getRegistry(config.get("gateway.host"), config.getInt("gateway.port")).lookup(config.get("gateway.name"));
                gateway.subscribeRobots(this);
                System.out.println("[Robot] Conectado ao Gateway com sucesso!");
            } catch (Exception e) {
                System.out.println("[Robot] Gateway não disponível, tentando novamente em 5 segundos...");
                Thread.sleep(5000);
            }
        }
    }

    private String cleanWord(String word) {
        // Remove todos os caracteres que não sejam letras (incluindo acentos) ou números
        String cleaned = word.replaceAll("[^a-zA-ZÀ-ÿ0-9]", "");
        // Retorna null se a palavra ficar vazia (era só caracteres especiais)
        return cleaned.isEmpty() ? null : cleaned;
    }

    public void extractInformationURL(String url, QueueInterface queue) throws java.rmi.RemoteException {
        try {
            Document doc;
            if ((doc = isLoginPage(url)) == null) {
                return;
            }

            // Extrai título
            String title = doc.title();
            if (title == null || title.isEmpty()) {
                title = url; // Fallback para o URL se não houver título
            }

            // Extrai snippet (primeiros 200 caracteres do texto)
            String snippet = doc.text();
            String language = this.detectLanguage(snippet);
            if (snippet.length() > 200) {
                snippet = snippet.substring(0, 200) + "...";
            }

            StringTokenizer tokens = new StringTokenizer(doc.text());

            // Extrai os URLs referenciados
            List<String> referencedUrls = extractReferencedUrls(doc, queue);

            List<String> novaPalavra = new ArrayList<>();

            BarrelInterface barrel = this.gateway.getNextBarrel("robot");

            while (tokens.hasMoreElements()){

                String rawWord = tokens.nextToken().toLowerCase();
    
                // Limpa a palavra removendo caracteres especiais
                String cleanedWord = cleanWord(rawWord);
                
                // Ignora se a palavra era só caracteres especiais
                if (cleanedWord == null) {
                    continue;
                }

                while (this.gateway.getsubscribedBarrel().isEmpty()) {
                    System.out.println("[Robot] Nenhum barrel disponível, aguardando...");
                    synchronized (this.lock) {
                        this.lock.wait(); // Espera até ser notificado
                    }
                    barrel = this.gateway.getNextBarrel("robot");
                }

                if (!barrel.isStopword(cleanedWord, language)) {
                    novaPalavra.add(cleanedWord);
                }
            }

            // Faz o multicast diretamente para os barrels
            this.multicastToBarrels(this.gateway, novaPalavra, url, referencedUrls, title, snippet, language);
        } catch (Exception e) {
            System.out.println(ANSI_RED + "[ROBOT] ERROR IN extractInformationURL" + ANSI_RESET);
            if (queue != null) {
                try {
                    queue.putNew(url, "robot"); // Reinsere o URL na fila para tentar novamente depois
                } catch (RemoteException ex) {
                    throw new RuntimeException("Erro na comunicação RMI", ex);
                }
            }
        }
    }

    public static void main(String[] args) throws RemoteException { 
        ConfigProperties config = new ConfigProperties();
        Robot robot = new Robot();
        QueueInterface queue = null;
        try {

            robot.connectToGateway(config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[Robot] Shutdown hook a correr, a limpar recursos...");
                try {
                    try { robot.gateway.unsubscribeRobot(robot); } catch (Exception ignore) { /* fallback */ }
                    try { UnicastRemoteObject.unexportObject(robot, true); } catch (Exception ignore) { /* fallback */ }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }));

        } catch (Exception e) {
            System.out.println(ANSI_RED + "[ROBOT] ERROR IN MAIN" + ANSI_RESET);
            e.printStackTrace();
        }

            while (true) {
                try {
                    // Tenta conectar à Queue se ainda não estiver conectado
                    while (queue == null) {
                        try {
                            queue = (QueueInterface) LocateRegistry.getRegistry(config.get("queue.host"), config.getInt("queue.port")).lookup(config.get("queue.name"));
                            System.out.println("[Robot] Conectado à Queue com sucesso!");
                        } catch (Exception e) {
                            System.out.println("[Robot] Queue não disponível, aguardando 5 segundos...");
                            synchronized (robot.lock) {
                                robot.lock.wait(5000); // Espera 5 segundos ou até ser notificado
                            }
                        }
                    }

                    String url;

                    // Tenta obter o próximo url
                    try {
                        url = queue.takeNext();

                        // Verifica se o URL é válido
                        if (url == null || url.isEmpty()) {
                            continue; // Volta ao início do while(true) e tenta novamente
                        }
                    } catch (Exception e) {
                        System.out.println("[Robot] Erro ao obter URL da queue: " + e.getMessage());
                        queue = null; // Marca como desconectado para tentar reconectar
                        continue;
                    }
                    
                    robot.extractInformationURL(url, queue);
                    
                } catch (Exception e) {
                    System.out.println(ANSI_RED + "[ROBOT] ERROR IN MAIN" + ANSI_RESET);
                    e.printStackTrace();
                }

            }
    }
}