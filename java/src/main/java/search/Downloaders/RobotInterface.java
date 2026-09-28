package search.Downloaders;

import java.rmi.Remote;

import search.Queue.QueueInterface;

public interface RobotInterface extends Remote{
	public void notifyrobot() throws java.rmi.RemoteException;
	public void extractInformationURL(String url, QueueInterface queue) throws java.rmi.RemoteException; 
}
