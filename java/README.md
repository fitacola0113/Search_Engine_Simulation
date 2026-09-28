# Sistema de Busca Distribuído (Google Lite)

Este projeto implementa um motor de busca distribuído em Java RMI, com componentes independentes: Gateway, Barrels, Queue, Robots e Client.

---

## Requisitos

- **Java JDK 17 ou superior**
- **Maven** (opcional, se quiser usar)
- **Jsoup** (biblioteca para parsing HTML, já incluída em `lib/`)

---

## Estrutura do Projeto

```
SD_project/
├── java/
│   ├── src/main/java/search/
│   │   ├── Barrels/
│   │   ├── Client/
│   │   ├── Downloaders/
│   │   ├── Gateway/
│   │   ├── Queue/
│   │   └── Properties/
│   ├── src/main/resources/config.properties
│   ├── lib/jsoup-1.15.3.jar
│   └── README.md
```

---

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

## Configuração

Edita o ficheiro `src/main/resources/config.properties` com os IPs e portas dos componentes.  
Se tudo estiver na mesma máquina, usa `localhost` para todos os hosts.

Exemplo:
```
gateway.host=localhost
gateway.port=7000
queue.host=localhost
queue.port=8183
barrel.host=localhost
barrel1.port=8184
client.host=localhost
robot.host=localhost
```

---

## Execução

**Ordem obrigatória de arranque:**

1. **Gateway**
   ```bash
   java -cp "bin;lib/jsoup-1.15.3.jar" search.Gateway.Gateway
   ```
2. **Queue**
   ```bash
   java -cp "bin;lib/jsoup-1.15.3.jar" search.Queue.Queue
   ```
3. **Barrel(s)**
   ```bash
   java -cp "bin;lib/jsoup-1.15.3.jar" search.Barrels.Barrel
   ```
   (Podes abrir vários Barrels em terminais diferentes)
4. **Robot(s)**
   ```bash
   java -cp "bin;lib/jsoup-1.15.3.jar" search.Downloaders.Robot
   ```
   (Podes abrir vários Robots em terminais diferentes)
5. **Client**
   ```bash
   java -cp "bin;lib/jsoup-1.15.3.jar" search.Client.Client
   ```

---

## Utilização

No Client, segue o menu interativo para pesquisar palavras, consultar referências, ver estatísticas, etc.

---

## Notas Importantes

- **Todos os componentes devem estar a correr antes de usar o Client.**
- Se usares máquinas diferentes, ajusta os IPs no `config.properties`.
- Se aparecer erro de "Connection refused", verifica firewall e IPs.
- Para adicionar novas URLs à Queue, usa o menu do Client.

---
