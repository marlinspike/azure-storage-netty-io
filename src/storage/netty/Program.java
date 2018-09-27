package storage.netty;
import java.io.File;
import java.util.Timer;
import picocli.CommandLine.Option;
import picocli.CommandLine;

import java.time.Duration;


public class Program implements Runnable {
	@Option(names = {"-a", "--account"}, required = true, description = "Azure Storage Account Name where the files uploaded should be stored.")
	private String AZURE_STORAGE_ACCOUNT_NAME = "";
	@Option(names = {"-c", "--container"}, required = true, description = "Container within which to store the files.")
	private String STORAGE_CONTAINER = "";
	@Option(names = {"-k", "--key"}, required = true, description = "Either the Primary or Secondary account key.")
	private String KEY = "";
	@Option(names = {"-d", "--directory"}, required = true, description = "Directory with files to upload to Azure.")
	private String DIRECTORY = "";


	public void run(){
		HTTPUploadClientAsync client2 = new HTTPUploadClientAsync(AZURE_STORAGE_ACCOUNT_NAME, KEY);

		try {
			long start = System.currentTimeMillis();
			
			System.out.println("Putting blob: " + DIRECTORY);
			client2.putBlob(DIRECTORY, STORAGE_CONTAINER);

			
			long stop = System.currentTimeMillis();
			System.out.println((stop - start)/1000 + "seconds");
			
		} catch (Exception e) {
			
			e.printStackTrace();
		}
	}
	
	public static void main( String[] args ){

		CommandLine.run(new Program(), args);
    }

}
