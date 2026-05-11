# Practical 8: FTP Backup Client Demo

This demo shows how to run the FTP backup client and verify the bonus features
(passive mode, versioned backups, deletion handling, and remote directory creation).

### Prerequisites

- Java JDK (javac + java available)
- An FTP server you can write to (vsftpd recommended for Linux/WSL)
- A local directory with .txt files to watch

### FTP server setup (Ubuntu/WSL example)

1) Install and configure vsftpd

```
sudo apt update
sudo apt install -y vsftpd
sudo mkdir -p /srv/ftp/backup
sudo chown $USER:$USER /srv/ftp/backup
```

2) Edit /etc/vsftpd.conf and ensure these settings exist

```
listen=YES
listen_ipv6=NO
anonymous_enable=NO
local_enable=YES
write_enable=YES
chroot_local_user=YES
allow_writeable_chroot=YES

pasv_enable=YES
pasv_min_port=40000
pasv_max_port=40100
```

3) Optional: lock FTP users to the backup directory (recommended for demo)

```
local_root=/srv/ftp/backup
```

4) Restart the server

- If systemd is available:
-
```
sudo systemctl restart vsftpd
```
- In WSL or non-systemd:

```
sudo service vsftpd restart
```

### Demo steps (basic + bonus features)

1) Compile the client

```
javac Practical8/FtpBackupClient.java
```

2) Run the client

```
java -cp Practical8 FtpBackupClient <host> <port> <user> <password> <localDir> <remoteDir> [--passive] [--versioned]
```

Example for WSL/local vsftpd with local_root set:

```
java -cp Practical8 FtpBackupClient 127.0.0.1 21 <user> '<password>' /home/<user>/COS332/Practical8 . --passive --versioned
```

If local_root is NOT set, use /srv/ftp/backup as the remote directory:

```
java -cp Practical8 FtpBackupClient 127.0.0.1 21 <user> '<password>' /home/<user>/COS332/Practical8 /srv/ftp/backup --passive --versioned
```

3) Trigger an upload (bonus: versioned backups)

```
echo "demo $(date)" >> /home/<user>/COS332/Practical8/demo.txt
```

Wait up to 60 seconds (poll interval). You should see remote files like:

- demo.txt.000 (first upload)
- demo.txt.001 (after a second change)

Check the server directory:

```
ls -l /srv/ftp/backup
```

4) Bonus: passive mode

Use `--passive` when the server is behind NAT (common in WSL/VMs). The client
will send `PASV` and connect to the server-provided data port.

5) Bonus: deletion handling

Delete a local file and observe that the client logs the deletion but does NOT
delete the remote history (safe backup behavior).

6) Stop the client

Press Ctrl+C in the terminal.

### Notes

- The client uploads only .txt files in the watched directory.
- Polling happens every 10 seconds, so multiple edits within that time may
  collapse into a single upload (acceptable per assignment).