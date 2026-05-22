package application.repository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import framework.DataRepository;

/**
 * RandomAccessFile를 이용하여 파일에서 데이터를 읽어오는 클래스
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 * @see {@link DataRepository}
 */
public class MMapStringParser implements DataRepository{
	private RandomAccessFile randomAccessFile = null;
	private FileChannel fc = null;
	private MappedByteBuffer mbb = null;
	private File mmapFile = null;
	
	/**
	 * <p>
	 * 메모리 맵으로 읽을 파일을 초기화한다.
	 * </p>
	 * 
	 * @param repoInfo 파일 경로
	 * @throws FileNotFoundException 파일이 존재하지 않는 경우
	 */
	public void init(Object repoInfo) throws FileNotFoundException{
		try{
			// 데이터 파일  오픈
			if(fc == null){
				mmapFile = new File(repoInfo.toString());
				randomAccessFile = new RandomAccessFile(mmapFile, "r");
				fc = randomAccessFile.getChannel();
			}

			// 파일을 메모리 맵으로 변환
			if(mbb == null)
				mbb = fc.map(FileChannel.MapMode.READ_ONLY, 0, mmapFile.length());
		}catch(FileNotFoundException ffe){
			throw new FileNotFoundException();
			}catch(Exception e){
				System.out.println("MMapStringFilter.openFile:"+e.getMessage());
				close();
			}
		}
	

	/**
	 * <p>
	 * 메모리 맵 파일에서 고정 길이 문자열을 읽어온다.
	 * </p>
	 * 
	 * @return Object 읽어들인 문자열, EOF인 경우 null
	 * @throws Exception 파일 읽기 시 에러 발생
	 */
	public Object getData() throws Exception{
		String readString = null;
		byte[] readBuffer = new byte[10];
		
		try{
			mbb.get(readBuffer);
			readString = new String(readBuffer);
		}catch(BufferUnderflowException bue){
			if(!mbb.hasRemaining()){
				close();
				return null;
			}

			for(int i=0; mbb.hasRemaining(); i++){
				readBuffer[i] = mbb.get();
			}
			readString = new String(readBuffer);
			close();
		}catch(Exception e){
			close();
			throw e;
		}
			
		return readString;
	}
		
	/**
	 * <p>
	 * 파일 읽기에 사용한 자원을 반납한다.
	 * </p>
	 */
	public void close(){
		try{
			if(fc != null){
				fc.close();
				fc = null;
			}
		}catch(Exception e){}
		try{
			if(randomAccessFile != null){
				randomAccessFile.close();
				randomAccessFile = null;
			}
		}catch(Exception e){}
		mbb = null;
	}
}
