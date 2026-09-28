package search.Barrels;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;

public interface BarrelInterface extends Remote { 
    public boolean isStopword(String palavra, String lang) throws RemoteException;
    public void WordsToAddToIndex(List<String> words, String url) throws java.rmi.RemoteException;
    public void receiveIndexUpdate(List<String> words, String url, long timestamp) throws java.rmi.RemoteException;
    public void receiveUrlReferenceUpdate(List<String> referencedUrls, String scannedUrl, long timestamp) throws java.rmi.RemoteException;
    public void receiveWordCountUpdate(Map<String, Integer> wordCounts, String url, long timestamp) throws java.rmi.RemoteException;
    public void receiveMetadataUpdate(UrlMetadata metadata, long timestamp) throws java.rmi.RemoteException;
    public void receiveUrlLanguageUpdate(String language, String url, long timestamp) throws RemoteException; // Ponto e virgula corrigido
    
    public UrlMetadata getUrlMetadata(String url) throws RemoteException;
    
    // Snapshots
    public Map<String, HashSet<String>> getIndexSnapshot() throws RemoteException;
    public Map<String, Set<String>> getUrlsReferencesSnapshot() throws RemoteException;
    public Map<String, UrlMetadata> getUrlMetadataSnapshot() throws RemoteException;
    public Map<String, List<String>> getUrlWordCountsSnapshot() throws RemoteException;
    
    // NOVOS SNAPSHOTS (Mapas)
    public Map<String, Set<String>> getStopwordsByLangSnapshot() throws RemoteException;
    public Map<String, String> getUrlLanguagesSnapshot() throws RemoteException;
    
    // Syncs
    public void syncIndex(Map<String, HashSet<String>> otherIndex) throws RemoteException;
    public void syncUrlReferences(Map<String, Set<String>> otherReferences) throws RemoteException;
    public void syncUrlMetadata(Map<String, UrlMetadata> otherMetadata) throws RemoteException;
    public void syncUrlWordCounts(Map<String, List<String>> otherWordCounts) throws RemoteException;
    public void syncStopWords(Map<String, Set<String>> otherStopwords) throws RemoteException;
    public void syncUrlLanguages(Map<String, String> otherLanguages) throws RemoteException;

    // Outros
    public int getReferenceCount(String url) throws RemoteException;
    public List<String> searchWord(String phrase) throws java.rmi.RemoteException;
    public List<String> searchUrl(String url) throws java.rmi.RemoteException;
    public void receiveQueue(BlockingDeque<String> queue, Map<String, Long> seenUrls) throws RemoteException;
    public BlockingDeque<String> getQueue() throws RemoteException;
    public Map<String, Long> getSeenUrls() throws RemoteException;
    public double getMeanTime() throws RemoteException;
    public int getSize() throws RemoteException;
    public String getBarrelName() throws RemoteException;
    public void sendUrlInfo(List<String> words, String url, List<String> referencedUrls, String title, String snippet,String language) throws RemoteException;
}