import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
  constructor() {
    this.client = null;
  }

  connect(onMessageCallback, onErrorCallback) {
    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      
      onConnect: () => {
        console.log('WebSocket conectado');
        
        this.client.subscribe('/topic/statistics', (message) => {
          try {
            const data = JSON.parse(message.body);
            onMessageCallback(data);
          } catch (error) {
            console.error('Erro ao processar mensagem WebSocket:', error);
          }
        });
      },
      
      onStompError: (frame) => {
        console.error('Erro WebSocket:', frame.headers['message']);
        if (onErrorCallback) {
          onErrorCallback(frame);
        }
      },
      
      onWebSocketError: (error) => {
        console.error('Erro de conexão WebSocket:', error);
        if (onErrorCallback) {
          onErrorCallback(error);
        }
      }
    });

    this.client.activate();
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      console.log('🔌 WebSocket desconectado');
    }
  }

  isConnected() {
    return this.client && this.client.connected;
  }
}

// ← Cria a instância com nome antes de exportar
const websocketService = new WebSocketService();

export default websocketService;