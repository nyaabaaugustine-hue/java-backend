
/**
 * Subject.java
 * 
 * Responsible for providing interface for the models
 * 
 * @author gusanthiago
 * @author hmmoreira
 */
public interface Subject {


	public void registerObserver(Observer observer);
	
	public void notifyObservers(long chatId, String studentsData);
}
