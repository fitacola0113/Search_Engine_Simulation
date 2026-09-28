package search.Client;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import search.Barrels.UrlMetadata;
import search.Gateway.GatewayInterface; 
import search.Properties.ConfigProperties; 

public class Client extends UnicastRemoteObject implements ClientInterface{

    private static Statistics stats = new Statistics();
    private GatewayInterface gateway;

    public Client() throws java.rmi.RemoteException {
        super();
    }

    @Override
    public void updateTopSearches(List<TopSearchEntry> topSearches) throws RemoteException {
        stats.setTopSearches(topSearches);
    }

    @Override
    public void updateAverageTime(double averageTime, String barrelName) throws RemoteException {
        stats.setAverageTime(averageTime,barrelName);
    }

    @Override
    public void updateIndexSize(int size, String barrelName) throws RemoteException {
        stats.setIndexSize(size, barrelName);
    }

    @Override
    public void deleteBarrel(String barrelName) throws RemoteException {
        stats.deleteBarrel(barrelName);
    }

    private void connectToGateway(ConfigProperties config) throws Exception {

        while (gateway == null) {
            try {
                gateway = (GatewayInterface) LocateRegistry.getRegistry(config.get("gateway.host"), config.getInt("gateway.port")).lookup(config.get("gateway.name"));
                gateway.subscribeClients(this);
                System.out.println("[Client] Conectado ao Gateway com sucesso!");
            } catch (Exception e) {
                System.out.println("[Client] Gateway não disponível, tentando novamente em 5 segundos...");
                Thread.sleep(5000);
            }
        }
    }

    public static void main(String[] args) {
        try {
            
            Client client = new Client();
            ConfigProperties config = new ConfigProperties();

            client.connectToGateway(config);

            client.gateway.sendStatistics(client);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[Client] Shutdown hook a correr, a limpar recursos...");
                try {
                    try { client.gateway.unsubscribeClient(client); } catch (Exception ignore) { /* fallback */ }
                    try { UnicastRemoteObject.unexportObject(client, true); } catch (Exception ignore) { /* fallback */ }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }));

            Scanner keyboard = new Scanner(System.in);
            String line;
            System.out.println("=".repeat(70));
            
            while(true) {
                System.out.println("\u001B[31mMenu do utilizador: (0-5)\u001B[0m");
                System.out.println("\u001B[31m0.\u001B[0m Sair.");
                System.out.println("\u001B[31m1.\u001B[0m URL novo para os robots explorarem.");
                System.out.println("\u001B[31m2.\u001B[0m Procurar por palavra ou frase.");
                System.out.println("\u001B[31m3.\u001B[0m Consultar lista de páginas com ligações para uma página específica.");
                System.out.println("\u001B[31m4.\u001B[0m Consultar top 10 pesquisas.");
                System.out.println("\u001B[31m5.\u001B[0m Consultar estatísticas de cada barrel ativo.");
                System.out.println("=".repeat(70));

                line = keyboard.nextLine();

                switch(line) {
                    case "0":
                        System.out.println("A sair...");
                        System.out.println("=".repeat(70));
                        break;
                    case "1":
                        System.out.println("Insere o URL:");
                        String url = keyboard.nextLine();
                        System.out.println(client.gateway.putNewUrl(url, "client"));
                        System.out.println("=".repeat(70));
                        break;
                    case "2":
                        System.out.println("Insere a palavra ou a frase:");
                        String frase = keyboard.nextLine();

                        int numpages = client.gateway.searchWordsUrls(client, frase);
                        int page = 1;
                        List<String> results;
                        Boolean continua = true;
                        while(continua) {
                            if(page <= 0) page = 1;
                            results = client.gateway.sendtop10(client,page); // Obtém os próximos 10 resultados
                            if (results.size() < 10 || results.size() == 11) {
                                for (int i = 0; i < results.size(); i++) {
                                    UrlMetadata meta = client.gateway.getUrlMetadata(results.get(i));
                                    // Verifica se meta é null
                                    if (meta == null) {
                                        System.out.println("\u001B[31m[" + (i+1) + "]\u001B[0m");
                                        System.out.println("\u001B[32m"+ results.get(i) + "\u001B[0m");
                                        System.out.println("\u001B[33mURL sem metadados disponíveis\u001B[0m\n");
                                        continue;
                                    }
                                    System.out.println("\u001B[31m[" + (i+1) + "]\u001B[0m");
                                    System.out.println("\u001B[36m" + meta.getTitle() + "\u001B[0m"); // Título em ciano
                                    System.out.println("\u001B[32m" + meta.getUrl() + "\u001B[0m");   // URL em verde
                                    System.out.println("\u001B[37m" + meta.getSnippet() + "\u001B[0m\n"); // Snippet em branco
                                }
                                results.removeAll(results);
                                continua = false;
                            } else {
                                for (int i = 0; i < results.size(); i++) {
                                    UrlMetadata meta = client.gateway.getUrlMetadata(results.get(i));
                                    if (meta == null) {
                                        System.out.println("\u001B[31m[" + (i+1) + "]\u001B[0m");
                                        System.out.println("\u001B[32m"+ results.get(i) + "\u001B[0m");
                                        System.out.println("\u001B[33mURL sem metadados disponíveis\u001B[0m\n");
                                        continue;
                                    }
                                    System.out.println("\u001B[31m[" + (i+1) + "]\u001B[0m");
                                    System.out.println("\u001B[36m" + meta.getTitle() + "\u001B[0m"); // Título em ciano
                                    System.out.println("\u001B[32m" + meta.getUrl() + "\u001B[0m");   // URL em verde
                                    System.out.println("\u001B[37m" + meta.getSnippet() + "\u001B[0m\n"); // Snippet em branco
                                }
                                results.removeAll(results);
                                System.out.println("Quer mais links? (1-Sim ou 2-Não)");
                                frase = keyboard.nextLine();
                                if (frase.equals("2")){ 
                                    continua = false;
                                }
                                else{
                                    System.out.println("Página: " + page + "/" + numpages + " Avançar(a) ou Recuar(r)");
                                    frase = keyboard.nextLine();
                                    if (frase.equals("a")) {
                                        page++;
                                    }else page--;
                                }

                            }
                        }
                        System.out.println("=".repeat(70));
                        break;
                    case "3":
                        System.out.println("Insere o URL:");
                        String link = keyboard.nextLine();

                        int numpage = client.gateway.searchUrls(client, link);
                        int pages = 1;
                        List<String> result;
                        Boolean cont = true;
                        while(cont) {
                            if(pages <= 0) pages = 1;
                            results = client.gateway.sendtop10(client,pages); // Obtém os próximos 10 resultados
                            if (results.size() < 10 || results.size() == 11) {
                                for (int i = 0; i < results.size(); i++) {
                                    UrlMetadata meta = client.gateway.getUrlMetadata(results.get(i));
                                    if (meta == null) {
                                        System.out.println("\u001B[31m[" + (i+1) + "]\u001B[0m");
                                        System.out.println("\u001B[32m"+ results.get(i) + "\u001B[0m");
                                        System.out.println("\u001B[33mURL sem metadados disponíveis\u001B[0m\n");
                                        continue;
                                    }
                                    System.out.println("\u001B[31m[" + (i+1) + "]\u001B[0m");
                                    System.out.println("\u001B[36m" + meta.getTitle() + "\u001B[0m"); // Título em ciano
                                    System.out.println("\u001B[32m" + meta.getUrl() + "\u001B[0m");   // URL em verde
                                    System.out.println("\u001B[37m" + meta.getSnippet() + "\u001B[0m\n"); // Snippet em branco
                                }
                                results.removeAll(results);
                                cont = false;
                            } else {
                                for (int i = 0; i < results.size(); i++) {
                                    UrlMetadata meta = client.gateway.getUrlMetadata(results.get(i));
                                    if (meta == null) {
                                        System.out.println("\u001B[31m[" + (i+1) + "]\u001B[0m");
                                        System.out.println("\u001B[32m"+ results.get(i) + "\u001B[0m");
                                        System.out.println("\u001B[33mURL sem metadados disponíveis\u001B[0m\n");
                                        continue;
                                    }
                                    System.out.println("\u001B[31m[" + (i+1) + "]\u001B[0m");
                                    System.out.println("\u001B[36m" + meta.getTitle() + "\u001B[0m"); // Título em ciano
                                    System.out.println("\u001B[32m" + meta.getUrl() + "\u001B[0m");   // URL em verde
                                    System.out.println("\u001B[37m" + meta.getSnippet() + "\u001B[0m\n"); // Snippet em branco
                                }
                                results.removeAll(results);
                                System.out.println("Quer mais links? (1-Sim ou 2-Não)");
                                frase = keyboard.nextLine();
                                if (frase.equals("2")){ 
                                    continua = false;
                                }
                                else{
                                    System.out.println("Página: " + pages + "/" + numpage + " \tAvançar(a) ou Recuar(r)");
                                    frase = keyboard.nextLine();
                                    if (frase.equals("a")) {
                                        pages++;
                                    }else pages--;
                                }
                            }
                        }
                        System.out.println("=".repeat(70));
                        break;
                    case "4":
                        // Top 10 palavras mais procuradas
                        System.out.println("=".repeat(70));
                        System.out.println("\u001B[31mTOP 10 PALAVRAS MAIS PROCURADAS: \u001B[0m");
                        System.out.println("=".repeat(70));
                        List<TopSearchEntry> top10 = stats.getTopSearches();
                        if (top10.isEmpty()) {
                            System.out.println("   Nenhuma pesquisa realizada ainda.");
                        } else {
                            for (int i = 0; i < top10.size(); i++) {
                                TopSearchEntry entry = top10.get(i);
                                System.out.printf("   %2d. %-30s %d pesquisas\n", 
                                    i + 1, entry.getWord(), entry.getCount());
                            }
                        }
                        System.out.println("=".repeat(70));
                        break;
                    case "5":
                        // Barrels ativos
                        System.out.println("=".repeat(70));
                        System.out.println("\u001B[31mBARRELS ATIVOS:\u001B[0m");
                        System.out.println("=".repeat(70));
                        Map<String, BarrelStats> activeBarrels = stats.getactiveBarrels();
                        if (activeBarrels.isEmpty()) {
                            System.out.println("   Nenhum barrel ativo.");
                        } else {
                            System.out.printf("   %-15s | %-20s | %s\n", 
                                "Nome", "Tempo Médio (ms)", "Tamanho Índice");
                            System.out.println("   " + "-".repeat(66));
                            
                            for (Map.Entry<String, BarrelStats> entry : activeBarrels.entrySet()) {
                                String barrelName = entry.getKey(); // Nome vem da chave do Map
                                BarrelStats stats = entry.getValue();
                                System.out.printf("   %-15s | %-20d | %d palavras\n",
                                    barrelName,
                                    stats.getAverageSearchTime(),
                                    stats.getIndexSize());
                            }
                        }
                        
                        System.out.println("=".repeat(70));
                        break;
                    default:
                        System.out.println("Deve estar entre 0 e 5\n");
                }

                if (line.equals("0")) break;

            }
            keyboard.close();

        } catch(Exception e) { 
            e.printStackTrace();
        }
    }
}
