# Sistema de Busca Distribuído (Googlo)

Este projeto implementa um motor de busca distribuído em Java RMI, com componentes independentes: Gateway, Barrels, Queue, Robots e Client.

---

## Requisitos

- **Java**: 17 ou superior
- **Maven**: 3.8 ou superior
- **Node.js**: 16 ou superior
- **npm**: 8 ou superior

## Instalação e Execução
## Instalação

1. **Clona ou copia o projeto para o teu computador**
2. **Compila o projeto**  
   - Com Maven:  
     ```bash
     mvn clean package
     ```
   - Manualmente:  
     ```bash
     javac -cp "lib/jsoup-1.15.3.jar" -d bin src/main/java/search/*/*.java
     ```

---
### 1. Clonar o Repositório

```bash
git clone <repository-url>
cd SD_project
```

### 2. Compilar o Backend

```bash
cd java
mvn clean install
```

### 3. Executar os Componentes (em terminais separados)

#### Gateway
```bash
cd java
java -cp target/classes search.Gateway.Gateway
```
- **Porta**: 8183
- **Nota**: Deve ser iniciado primeiro

#### Queue
```bash
cd java
java -cp target/classes search.Queue.Queue
```
- **Porta**: 8184

#### Barrel (pode executar múltiplos)
```bash
cd java
java -cp target/classes search.Barrels.Barrel
```
- **Portas**: 8185, 8186... (incrementa automaticamente, excepto 8443)
- **Persistência**: Cada barrel salva estado em `barrel<N>_<hostname>.obj`

#### Robot (pode executar múltiplos)
```bash
cd java
java -cp target/classes search.Downloaders.Robot
```

#### Web Server
```bash
cd java
mvn spring-boot:run
```
- **Porta HTTP**: 8080
- **Porta HTTPS**: 8443
- **WebSocket endpoint**: ws://localhost:8080/ws

### 4. Executar o Frontend

```bash
cd frontend
npm install
npm run build
npm run start
```
- **URL**: https://localhost:3000
- **Nota**: Abre automaticamente no navegador

### Ordem de Inicialização Recomendada

1. Gateway
2. Queue
3. Barrel(s)
4. Robot(s)
5. Web Server
6. Frontend



## Configuração

Edita o ficheiro `src/main/resources/config.properties` com os IPs e portas dos componentes.  
Se tudo estiver na mesma máquina, usa `localhost` para todos os hosts.

Exemplo:
```
gateway.host=localhost
gateway.port=8183
queue.host=localhost
queue.port=8184
barrel.host=localhost
barrel1.port=8185
barrel.host=localhost
barrel1.port=8186
server.port=8443
```

---
## Estrutura do Projeto

```
SD_project/
├── java/                           # Backend Java
│   ├── src/main/java/search/
│   │   ├── Barrels/               # Lógica dos Barrels
│   │   │   ├── Barrel.java
│   │   │   ├── BarrelInterface.java
│   │   │   └── UrlMetadata.java
│   │   ├── Client/                # Interface cliente RMI
│   │   │   ├── Client.java
│   │   │   ├── ClientInterface.java
│   │   │   └── Statistics.java
│   │   ├── Downloaders/           # Robots de crawling
│   │   │   ├── Robot.java
│   │   │   └── RobotInterface.java
│   │   ├── Gateway/               # Gateway principal
│   │   │   ├── Gateway.java
│   │   │   └── GatewayInterface.java
│   │   ├── Queue/                 # Queue de URLs
│   │   │   ├── Queue.java
│   │   │   └── QueueInterface.java
│   │   ├── WebServer/             # Spring Boot server
│   │   │   ├── SearchController.java
│   │   │   ├── ClientService.java
│   │   │   ├── WebSocketConfig.java
│   │   │   └── CorsConfig.java
│   │   ├── Properties/            # Configurações
│   │   │   ├── config.properties
│   │   │   └── ConfigProperties.java
│   │   └── WebServerApplication.java
│   ├── src/main/resources/
│   │   ├── models/                # Modelos de deteção de idioma
│   │   ├── templates/             # Templates Thymeleaf (legacy)
│   │   ├── application.properties
│   │   └── keystore.p12          # Certificado SSL
│   └── pom.xml
│
├── frontend/                      # Frontend React
│   ├── public/
│   │   └── index.html
│   ├── src/
│   │   ├── components/
│   │   │   ├── SearchPage.js     # Página principal
│   │   │   ├── SearchResults.js  # Resultados de pesquisa
│   │   │   ├── Statistics.js     # Estatísticas
│   │   │   ├── IndexUrl.js       # Indexar URL manual
│   │   │   ├── Backlinks.js      # Consultar backlinks
│   │   │   └── site_assets/      # Componentes visuais
│   │   │       ├── GlassSurface.js
│   │   │       ├── LoadingCat.js
│   │   │       └── MetaBalls.js
│   │   ├── services/
│   │   │   ├── api.js            # Cliente HTTP (Axios)
│   │   │   └── websocket.js      # Cliente WebSocket
│   │   ├── assets/               # Imagens
│   │   ├── App.js
│   │   └── index.js
│   └── package.json
│
│
├── README.md                      # Este ficheiro
└── .gitignore
```
## Notas Importantes

- **Todos os componentes devem estar a correr antes de usar o Client.**
- Se usares máquinas diferentes, ajusta os IPs no `config.properties`.
- Se aparecer erro de "Connection refused", verifica firewall e IPs.
- Para adicionar novas URLs à Queue, usa o menu do Client.

---
