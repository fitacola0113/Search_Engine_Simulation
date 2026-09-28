package search.Client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ClientInterface extends Remote{
    public void updateTopSearches(List<TopSearchEntry> topSearches) throws RemoteException;
    public void updateAverageTime(double averageTime, String barrelName) throws RemoteException;
    public void updateIndexSize(int size, String barrelName) throws RemoteException;
    public void deleteBarrel(String barrelName) throws RemoteException;
}
