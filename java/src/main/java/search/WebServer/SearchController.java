package search.WebServer;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct; 
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import search.Barrels.UrlMetadata; 
import search.Client.ClientInterface;
import search.Client.Statistics;
import search.Client.TopSearchEntry;
import search.Gateway.GatewayInterface;

@RestController
@RequestMapping("/api")
public class SearchController extends UnicastRemoteObject implements ClientInterface {

    @Autowired
    private ClientService clientService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private GatewayInterface gateway;

    private volatile Statistics stats = new Statistics();

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private String openRouterApiKey;

    public SearchController() throws RemoteException {
        super();
        // Carrega o .env da raiz do projeto java/
        Dotenv dotenv = Dotenv.configure()
        .directory("./java")  
        .ignoreIfMissing()
        .load();
    
        this.openRouterApiKey = dotenv.get("OPENROUTER_API_KEY");
    }
    
    @PostConstruct
    public void init() {
        try {
            gateway = clientService.getGateway();
            gateway.subscribeClients(this);
            gateway.sendStatistics(this);
            System.out.println("[WebServer] Controller registado no Gateway");

             // Validação da API key
            if (openRouterApiKey == null || openRouterApiKey.isEmpty()) {
                log.warn("AVISO: OpenRouter API key não configurada! AI summaries não funcionarão.");
            } else {
                log.info("OpenRouter API key carregada com sucesso");
            }

        } catch (Exception e) {
            System.err.println("[WebServer] Erro ao registar no Gateway: " + e.getMessage());
        }
    }

    // ============================================================
    // Implementação da interface ClientInterface (WebSocket Push)
    // ============================================================
    
    @Override
    public void updateTopSearches(List<TopSearchEntry> topSearches) throws RemoteException {
        stats.setTopSearches(topSearches);
        System.out.println("[WebSocket] Enviando atualização de Top Searches...");
        messagingTemplate.convertAndSend("/topic/statistics", stats);
    }

    @Override
    public void updateAverageTime(double averageTime, String barrelName) throws RemoteException {
        stats.setAverageTime(averageTime, barrelName);
        System.out.println("[WebSocket] Enviando atualização de Tempo Médio...");
        messagingTemplate.convertAndSend("/topic/statistics", stats);
    }

    @Override
    public void updateIndexSize(int size, String barrelName) throws RemoteException {
        stats.setIndexSize(size, barrelName);
        messagingTemplate.convertAndSend("/topic/statistics", stats);
    }

    @Override
    public void deleteBarrel(String barrelName) throws RemoteException {
        stats.deleteBarrel(barrelName);
        System.out.println("[WebSocket] Enviando atualização de Barrel Removido...");
        messagingTemplate.convertAndSend("/topic/statistics", stats);
    }

    // ============================================================
    // Métodos auxiliares para IA
    // ============================================================

    private String parseAISummary(String jsonResponse) {
        try {
            log.info("Tentando fazer parse do JSON...");
            
            // Procura por "content":"
            int contentStart = jsonResponse.indexOf("\"content\":\"");
            if (contentStart == -1) {
                log.error("Campo 'content' não encontrado no JSON");
                return "Resumo não disponível.";
            }
            
            contentStart += 11; // Tamanho de "content":"
            
            // Procura pelo fim - próximo
            int contentEnd = contentStart;
            while (contentEnd < jsonResponse.length()) {
                if (jsonResponse.charAt(contentEnd) == '"' && 
                    (contentEnd == 0 || jsonResponse.charAt(contentEnd - 1) != '\\')) {
                    break;
                }
                contentEnd++;
            }
            
            if (contentEnd >= jsonResponse.length()) {
                return "Formato de resposta inválido.";
            }
            
            String content = jsonResponse.substring(contentStart, contentEnd);
            
            // Decodifica escapes JSON
            content = content
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
            
            if (content.trim().isEmpty()) {
                log.warn("Conteúdo vazio recebido da IA");
                return "A IA não gerou um resumo para esta pesquisa.";
            }
            
            return content;
            
        } catch (Exception e) {
            log.error("Erro ao fazer parse: " + e.getMessage());
            return "Erro ao processar resumo.";
        }
    }

    // ============================================================
    // API REST Endpoints
    // ============================================================

   /**
     * Pesquisar palavras
     * GET /api/search?query=exemplo&page=1&aiEnabled=true
     * Retorna: { results: [...], query: "exemplo", totalPages: 4, currentPage: 1, aiSummary: "...", error: "..." }
     */
    @GetMapping("/search")
    public Map<String, Object> search(
        @RequestParam(name="query", required=true) String query,
        @RequestParam(name="page", defaultValue="1") Integer page,
        @RequestParam(name="aiEnabled", defaultValue="false") Boolean aiEnabled
    ) {
        Map<String, Object> response = new HashMap<>();
        
        if (gateway == null) {
            response.put("error", "Gateway offline ou não inicializado.");
            return response;
        }

        try {
            Integer totalPages = gateway.searchWordsUrls(this, query);
            List<String> results = gateway.sendtop10(this, page);
            
            List<UrlMetadata> metadataList = new ArrayList<>();
            for (String url : results) {
                if (url.equals("Não tem mais urls")) break;
                UrlMetadata meta = gateway.getUrlMetadata(url);
                if (meta != null) {
                    // Busca o número real de referências
                    int refCount = gateway.getReferenceCount(url);
                    meta.setReferenceCount(refCount);
                    metadataList.add(meta);
                } else {
                    UrlMetadata emptyMeta = new UrlMetadata(url, url, "URL sem metadados disponíveis");
                    metadataList.add(emptyMeta);
                }
            }
            
            response.put("results", metadataList);
            response.put("query", query);
            response.put("totalPages", totalPages);
            response.put("currentPage", page);

            // ============================================================
            // Obter resumo da IA se aiEnabled = true
            // ============================================================
            if (aiEnabled && page == 1 && !metadataList.isEmpty()) { 
                // Verifica se a API key está configurada
                if (openRouterApiKey == null || openRouterApiKey.isEmpty()) {
                    log.warn("API key não configurada. AI summary desativado.");
                    response.put("aiSummary", "AI summary não disponível: API key não configurada.");
                } else {
                    try {
                        // Constrói o contexto baseado nos snippets dos resultados
                        StringBuilder contextBuilder = new StringBuilder();
                        contextBuilder.append("Based on these search results about \"").append(query).append("\":\n\n");
                        
                        for (int i = 0; i < metadataList.size(); i++) {
                            UrlMetadata meta = metadataList.get(i);
                            contextBuilder.append((i + 1)).append(". ");
                            
                            // Adiciona título se disponível
                            if (meta.getTitle() != null && !meta.getTitle().equals(meta.getUrl())) {
                                contextBuilder.append(meta.getTitle()).append("\n");
                            }
                            
                            // Adiciona snippet se disponível
                            if (meta.getSnippet() != null && !meta.getSnippet().equals("URL sem metadados disponíveis")) {
                                contextBuilder.append(meta.getSnippet()).append("\n");
                            }
                            
                            contextBuilder.append("\n");
                        }
                        
                        contextBuilder.append("\nProvide a concise summary of the main topics and key information of ").append(query);
                        
                        String aiContext = contextBuilder.toString();
                        log.info("Contexto enviado para IA: " + aiContext);
                        
                        HttpResponse<String> aiResponse = Unirest.post("https://openrouter.ai/api/v1/chat/completions")
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + openRouterApiKey)
                            .body("{\n  \"model\": \"kwaipilot/kat-coder-pro:free\",\n  \"messages\": [\n    {\n      \"role\": \"system\",\n      \"content\": \"You are a concise content summarizer. Provide only the core information in bullet points or short paragraphs. Do not include introductions, explanations of what you will do, or conclusions. Start directly with the main content.\"\n    },\n    {\n      \"role\": \"user\",\n      \"content\": \"" + aiContext.replace("\"", "\\\"").replace("\n", "\\n") + "\"\n    }\n  ]\n}")
                            .asString();

                        // Parse a resposta JSON para obter o conteúdo
                        String summary = parseAISummary(aiResponse.getBody());
                        log.info("JSON completo: " + aiResponse.getBody());
                        log.info("Status code: " + aiResponse.getStatus());
                        log.info("Mensagem AI: " + summary);
                        
                        response.put("aiSummary", summary);

                    } catch (Exception e) {
                        log.error("Erro ao obter resumo da IA: " + e.getMessage());
                        response.put("aiSummary", null);
                    }
                }
            } else {
                response.put("aiSummary", null);
            }
            
        } catch (RemoteException e) {
            response.put("error", "Erro na pesquisa: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * Carregar mais resultados (paginação)
     * GET /api/search/more
     * Retorna: [UrlMetadata, UrlMetadata, ...]
     */
    @GetMapping("/search/more")
    public List<UrlMetadata> loadMoreResults(@RequestParam(name="page", defaultValue="2") Integer page) {
        List<UrlMetadata> metadataList = new ArrayList<>();
        
        if (gateway == null) {
            return metadataList;
        }
        
        try {
            List<String> results = gateway.sendtop10(this, page); // ← ADICIONA page
            
            for (String url : results) {
                if (url.equals("Não tem mais urls")) break;
                UrlMetadata meta = gateway.getUrlMetadata(url);
                if (meta != null) {
                    metadataList.add(meta);
                } else {
                    metadataList.add(new UrlMetadata(url, url, "URL sem metadados disponíveis"));
                }
            }
        } catch (RemoteException e) {
            System.err.println("[WebServer] Erro ao carregar mais resultados: " + e.getMessage());
        }
        
        return metadataList;
    }

    /**
     * Indexar novo URL
     * POST /api/index?url=https://example.com
     * Retorna: String com mensagem de sucesso/erro
     */
    @PostMapping("/index")
    public Map<String, String> indexUrl(@RequestParam("url") String url) {
        Map<String, String> response = new HashMap<>();
        
        if (gateway == null) {
            response.put("message", "Erro: Gateway offline");
            response.put("status", "error");
            return response;
        }

        try {
            String result = gateway.putNewUrl(url, "client");
            response.put("message", result);
            response.put("status", "success");
        } catch (RemoteException e) {
            response.put("message", "Erro ao indexar: " + e.getMessage());
            response.put("status", "error");
        }
        
        return response;
    }

    /**
     * Consultar backlinks de um URL
     * GET /api/backlinks?url=https://example.com&page=1
     * Retorna: { backlinks: [...], targetUrl: "...", totalPages: 3, currentPage: 1, error: "..." }
     */
    @GetMapping("/backlinks")
    public Map<String, Object> getBacklinks(
        @RequestParam(name="url", required=true) String url,
        @RequestParam(name="page", defaultValue="1") Integer page
    ) {
        Map<String, Object> response = new HashMap<>();
        
        if (gateway == null) {
            response.put("error", "Gateway offline");
            return response;
        }

        try {
            Integer totalPages = gateway.searchUrls(this, url);
            List<String> results = gateway.sendtop10(this, page);
            
            List<UrlMetadata> metadataList = new ArrayList<>();
            for (String resultUrl : results) {
                if (resultUrl.equals("Não tem mais urls")) break;
                UrlMetadata meta = gateway.getUrlMetadata(resultUrl);
                if (meta != null) {
                    metadataList.add(meta);
                } else {
                    metadataList.add(new UrlMetadata(resultUrl, resultUrl, "URL sem metadados disponíveis"));
                }
            }
            
            response.put("targetUrl", url);
            response.put("backlinks", metadataList);
            response.put("totalPages", totalPages);
            response.put("currentPage", page);
            
        } catch (RemoteException e) {
            response.put("error", "Erro ao obter backlinks: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * Buscar e indexar as top stories do Hacker News
     * POST /api/hackernews
     */
    @PostMapping("/hackernews")
    public Map<String, String> hackerNews() {
        Map<String, String> response = new HashMap<>();
        
        try {
            String topStoriesEndpoint = "https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty";

            RestTemplate restTemplate = new RestTemplate();
            List hackerNewsNewTopStories = restTemplate.getForObject(topStoriesEndpoint, List.class);

            assert hackerNewsNewTopStories != null;
            log.info("hackerNewsNewStories: " + hackerNewsNewTopStories);
            log.info("hackerNewsNewStories: " + hackerNewsNewTopStories.size()); // Up to 500 top stories

            for (int i = 0; i <= 50; i++) { // Apenas retira 50 stories
                Integer storyId = (Integer) hackerNewsNewTopStories.get(i);

                String storyItemDetailsEndpoint = String.format("https://hacker-news.firebaseio.com/v0/item/%s.json?print=pretty", storyId);
                HackerNewsItemRecord hackerNewsItemRecord = restTemplate.getForObject(storyItemDetailsEndpoint, HackerNewsItemRecord.class);

                if (hackerNewsItemRecord == null) {
                    response.put("message", "Não conseguiu aceder ao Hacker News");
                    response.put("status", "error");
                    continue;
                }

                if(hackerNewsItemRecord.url() != null) {
                    log.info("Story URL: " + hackerNewsItemRecord.url());
                    gateway.putHackerNews(hackerNewsItemRecord.url(), null);
                    response.put("message", "Put URL info in Barrel" + hackerNewsItemRecord.url());
                    response.put("status", "success");
                }
            }
        } catch (RemoteException e) {
            response.put("status", "Erro ao meter urls do Hacker News: " + e.getMessage());
        }
        return response;
    }

    /**
     * Carregar mais backlinks (paginação)
     * GET /api/backlinks/more
     * Retorna: [UrlMetadata, UrlMetadata, ...]
     */
    @GetMapping("/backlinks/more")
    public List<UrlMetadata> loadMoreBacklinks(@RequestParam(name="page", defaultValue="2") Integer page) {
        List<UrlMetadata> metadataList = new ArrayList<>();
        
        if (gateway == null) {
            return metadataList;
        }
        
        try {
            List<String> results = gateway.sendtop10(this, page); // ← ADICIONA page
            
            for (String url : results) {
                if (url.equals("Não tem mais urls")) break;
                UrlMetadata meta = gateway.getUrlMetadata(url);
                if (meta != null) {
                    metadataList.add(meta);
                } else {
                    metadataList.add(new UrlMetadata(url, url, "URL sem metadados disponíveis"));
                }
            }
        } catch (RemoteException e) {
            System.err.println("[WebServer] Erro ao carregar mais backlinks: " + e.getMessage());
        }
        
        return metadataList;
    }

    /**
     * Obter estatísticas (PULL inicial)
     * GET /api/statistics
     * Retorna: Statistics { topSearches: [...], activeBarrels: {...} }
     */
    @GetMapping("/statistics")
    public Statistics getStatistics() {
        return stats;
    }
}