package search.Queue;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface QueueInterface extends Remote {
    String takeNext() throws RemoteException;
    public void putNew(String url,String id) throws java.rmi.RemoteException;
}
