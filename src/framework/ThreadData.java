package framework;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * 작업 스레드에 전달할 데이타 LinkedBlockingQueue 클래스<br>
 * 블럭킹을 위해 LinkedBlockingQueue를 상속받는다.
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 */
public class ThreadData extends LinkedBlockingQueue<ThreadJob>{
	
}
