package application.repository;

/**
 * RandomAccessFile를 이용하여 파일에서 데이터를 읽어오는 클래스
 * 
 * @author dhlee
 * @version 1.0
 * @since 1.0
 * @see {@link StringParser}
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import framework.DataRepository;

public class MMapStringParser implements DataRepository{
	private FileChannel fc = null;
	private MappedByteBuffer mbb = null;
	private File mmapFile = null;
	
	public void init(Object repoInfo) throws FileNotFoundException{
		try{
			// 데이터 파일  오픈
			if(fc == null){
				mmapFile = new File(repoInfo.toString());
				fc = new RandomAccessFile(mmapFile, "r").getChannel();
			}

			// 파일을 메모리 맵으로 변환
			if(mbb == null)
				mbb = fc.map(FileChannel.MapMode.READ_ONLY, 0, mmapFile.length());
		}catch(FileNotFoundException ffe){
			throw new FileNotFoundException();
		}catch(Exception e){
			System.out.println("MMapStringFilter.openFile:"+e.getMessage());
			try{ fc.close(); }catch(Exception e2){}
		}
	}
	

	public Object getData(){
		
		String readString = null;
		byte[] readBuffer = new byte[10];
		
		try{
			mbb.get(readBuffer);
			readString = new String(readBuffer);
		}catch(BufferUnderflowException bue){
			if(mbb.hasRemaining()){
				for(int i=0; mbb.hasRemaining(); i++){
					try{
						readBuffer[i] = mbb.get();
					}catch(BufferUnderflowException bue2){
						break;
					}catch(Exception e){
						e.printStackTrace();
						break;
					}
				}
				readString = new String(readBuffer);
			}else{
				return null;
			}
			try{ fc.close(); }catch(Exception e2){}
		}catch(Exception e){
			System.out.println("MMapStringFilter.filter:"+e.getMessage());
			try{ fc.close(); }catch(Exception e2){}
		}
		
		return readString;
	}
}
