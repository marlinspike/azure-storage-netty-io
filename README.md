# azure-storage-netty-io

Using Netty to write to Azure storage

To use:

- Run (via Maven): 
mvn exec:java -Dexec.mainClass="storage.netty.Program" -Dexec.args="-a <STORAGE_ACCOUNT_NAME> -c <CONTAINER_NAME> -d <FOLDER_WITH_FILES_TO_UPLOAD> -k <KEY>"


Command Line Options:
- a: Storage Account Name
- c: Container Name
- d: Directory with files to upload 
- k: Primary or Secondary Key of Storage Account
