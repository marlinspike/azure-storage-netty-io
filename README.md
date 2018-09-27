# azure-storage-netty-io

Using Netty to write to Azure storage

To use:

- Edit Program.java and replace placeholders for and with valid ones. Please remember not to check account keys in to public repos.

- Run (via Maven): mvn exec:java -Dexec.mainClass="storage.netty.Program" -Dexec.args="<folder_with_files> <target_azure_container_name>"

For example: mvn exec:java -Dexec.mainClass="storage.netty.Program" -Dexec.args="/Users/tintin/code/java/azure-storage-netty/files/ marlinspike"
