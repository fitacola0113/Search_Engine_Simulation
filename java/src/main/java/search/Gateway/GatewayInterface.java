package search.Gateway;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;

import search.Barrels.BarrelInterface;
import search.Barrels.UrlMetadata;
import search.Client.ClientInterface;
import search.Downloaders.RobotInterface;
import search.Queue.QueueInterface; 

public interface  GatewayInterface extends Remote{
    public BarrelInterface getNextBarrel(String id) throws RemoteException;
    public void reliableMulticast(BarrelInterface barrel)throws RemoteException;
    public List<String> sendtop10(ClientInterface client, Integer numPage) throws RemoteException;
    public void sendMeanTime(double meanTime, String barrelName) throws RemoteException;
    public void sendSize(int size, String barrelName) throws RemoteException;
    public void sendStatistics(ClientInterface client) throws RemoteException;
    public void sendStatisticsBarrel(BarrelInterface barrel) throws RemoteException;
    public List<BarrelInterface> getsubscribedBarrel() throws RemoteException;
    public String putNewUrl(String line,String id) throws RemoteException;
    public Integer searchWordsUrls(ClientInterface client, String line) throws RemoteException;
    public Integer searchUrls(ClientInterface client, String url) throws RemoteException;
    public void sendQueue(BlockingDeque<String> queue, Map<String, Long> seenUrls) throws RemoteException;
    public BlockingDeque<String> getQueue() throws RemoteException;
    public UrlMetadata getUrlMetadata(String url) throws RemoteException;
    public Map<String, Long> getSeenUrls() throws RemoteException;
    public void putHackerNews(String url, QueueInterface queue) throws RemoteException;
    public void subscribeClients(ClientInterface client) throws RemoteException;
    public void subscribeBarrels(BarrelInterface barrel) throws RemoteException;
    public void subscribeRobots(RobotInterface robot) throws RemoteException;
    public void subscribeQueue(QueueInterface queue) throws RemoteException;
    public void unsubscribeQueue(QueueInterface queue) throws RemoteException;
    public void unsubscribeBarrel(BarrelInterface barrel, String barrelName) throws RemoteException;
    public void unsubscribeClient(ClientInterface client) throws RemoteException;
    public void unsubscribeRobot(RobotInterface robot) throws RemoteException;
    public int getReferenceCount(String url) throws RemoteException;
}
