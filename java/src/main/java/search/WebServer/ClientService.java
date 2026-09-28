package search.WebServer;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.springframework.stereotype.Service;

import search.Gateway.GatewayInterface;
import search.Properties.ConfigProperties;

@Service
public class ClientService {
    
    private GatewayInterface gateway;
    private ConfigProperties config;
    
    public ClientService() {
        try {
            config = new ConfigProperties();
            connectToGateway();
        } catch (Exception e) {
            System.err.println("[WebServer] Erro ao conectar ao Gateway: " + e.getMessage());
        }
    }
    
    private void connectToGateway() throws Exception {
        String gatewayHost = config.get("gateway.host");
        int gatewayPort = config.getInt("gateway.port");
        String gatewayName = config.get("gateway.name");
        
        while (gateway == null) {
            try {
                Registry registry = LocateRegistry.getRegistry(gatewayHost, gatewayPort);
                gateway = (GatewayInterface) registry.lookup(gatewayName);
                System.out.println("[WebServer] Conectado ao Gateway");
            } catch (Exception e) {
                System.out.println("[WebServer] Gateway não disponível, tentando novamente em 5 segundos...");
                Thread.sleep(5000);
            }
        }
    }
    
    public GatewayInterface getGateway() {
        return gateway;
    }
    
    public boolean isConnected() {
        return gateway != null;
    }
}