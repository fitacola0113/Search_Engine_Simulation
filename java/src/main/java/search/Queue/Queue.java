package search.Queue;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry; 
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import search.Gateway.GatewayInterface;
import search.Properties.ConfigProperties;

public class Queue extends UnicastRemoteObject implements QueueInterface {

    private BlockingDeque<String> queue = new LinkedBlockingDeque<>(); //QUEUE
    private Map<String, Long> seenUrls = new HashMap<>(); // URL -> timestamp
    private final long EXPIRATION = 1L * 60 * 1000; // 48h
    private GatewayInterface gateway;

    public Queue() throws java.rmi.RemoteException {
        super();
    }

    private void connectToGateway(ConfigProperties config) throws Exception {

        while (gateway == null) {
            try {
                gateway = (GatewayInterface) LocateRegistry.getRegistry(config.get("gateway.host"), config.getInt("gateway.port")).lookup(config.get("gateway.name"));
                gateway.subscribeQueue(this);
                System.out.println("[Queue] Conectado ao Gateway com sucesso!");
            } catch (Exception e) {
                System.out.println("[Queue] Gateway não disponível, tentando novamente em 5 segundos...");
                Thread.sleep(5000);
            }
        }
    }
    
    public static void main(String[] args) {
        
        try {

            ConfigProperties config = new ConfigProperties();
            System.setProperty("java.rmi.server.hostname", config.get("queue.host"));
        
            Queue queue = new Queue();

            // Conecta ao registry criado pelo Gateway
            Registry registry = LocateRegistry.createRegistry(config.getInt("queue.port"));
            registry.bind(config.get("queue.name"), queue);
            System.out.println("[Queue] Criou registo!");

            queue.connectToGateway(config);

            BlockingDeque<String> retrievedQueue = queue.gateway.getQueue();
            Map<String, Long> retrievedSeenUrls = queue.gateway.getSeenUrls();
            
            // Só atribui se não for null
            if (retrievedQueue != null) {
                queue.queue = retrievedQueue;
            }
            if (retrievedSeenUrls != null) {
                queue.seenUrls = retrievedSeenUrls;
            }

            final Object lock = new Object();

            // Shutdown hook para tratar Ctrl+C (SIGINT) e encerramentos
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[Queue] Shutdown hook a correr, a limpar recursos...");
                try {
                    // tenta desinscrever no gateway (ajuste se o método tiver outro nome)
                    queue.gateway.sendQueue(queue.queue, queue.seenUrls);
 
                    try { queue.gateway.unsubscribeQueue(queue); } catch (Exception ignore) { /* fallback */ }
                    // Remove a exportação dos objetos remotos 'queue' e 'registry' do RMI.
                    // Isso garante que, ao encerrar a aplicação, os recursos de rede e portas sejam libertados,
                    // evitando que os objetos continuem acessíveis remotamente ou causem conflitos futuros.
                    try { UnicastRemoteObject.unexportObject(queue, true); } catch (Exception ignore) { /* fallback */ }
                    try { UnicastRemoteObject.unexportObject(registry, true); } catch (Exception ignore) { /* fallback */ }
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    // notificar a thread principal que está em wait()
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            }));

            synchronized (lock) {
                lock.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String takeNext() throws RemoteException {

        try {
            // Espera no máximo 2 segundos por um URL
            String nextUrl = queue.pollFirst(2, java.util.concurrent.TimeUnit.SECONDS);
            
            if (nextUrl == null) {
                return ""; // Retorna string vazia se timeout
            }
            System.out.println("Url metido na queue: " + nextUrl);
            return nextUrl;
            
        } catch (InterruptedException e) {
            
            System.out.println("[Queue] Interrompido ao buscar URL");
            Thread.currentThread().interrupt();
            return "";
        }
    }

    @Override
    public synchronized void putNew(String url,String id) throws java.rmi.RemoteException {

        BlockingDeque<String> queue = this.queue;

        long now = System.currentTimeMillis();

        // Limpa URLs expiradas do seenUrls
        seenUrls.entrySet().removeIf(entry -> now - entry.getValue() > EXPIRATION);

        // Só adiciona se não estiver na seenUrls
        try {
            if (!seenUrls.containsKey(url)) {
                seenUrls.put(url, now);
                if ("client".equals(id)) {
                    queue.putFirst(url);
                    System.out.println("Url metido na queue: " + url);
                } else if ("crawler".equals(id)) {
                    queue.putLast(url);
                }
             }

        } catch (Exception e) {
            System.err.println("Não conseguiu colocar o url");
        }

        if (queue.isEmpty()) {
            notifyAll();
        }

    }
}
