package napster;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Peer {
	public static int servPort = 10098;
	public static int   myPort;// = 9697;
	public static Scanner scan = new Scanner(System.in);
	
	public static UDPAliveReceiver       UDPaliveThread = new UDPAliveReceiver();
	public static UDPRequestsReceiver UDPrequestsThread = new UDPRequestsReceiver();
	public static TCPPeerReceiver       TCPreceivThread;

	public static DatagramSocket UDPservSocket;
	public static DatagramSocket UDPcliSocket;
	public static ServerSocket   TCPservSocket;
	public static Socket         TCPcliSocket;
	
	public static String myFolder; //= "C:\\Users\\Pedro M\\Desktop\\Peer1";
	
	public static void main(String[] args) {
		try {
			//em que porta vc quer inicializar esse peer?
			//em que pasta vc quer inicializar esse peer?
		    UDPservSocket = new DatagramSocket();              //alive vai ouvir aqui
		    myPort = UDPservSocket.getLocalPort();
		    System.out.println("minha porta fixa é: "+myPort); //ajuda a identificar terminais
			UDPcliSocket  = new DatagramSocket();              //requests vao sair daqui
			TCPservSocket = new ServerSocket(myPort);          //ouve DOWNLOAD requests aqui
			UDPaliveThread.start();
			UDPrequestsThread.start();
			while(true) {
			    Socket s = TCPservSocket.accept();
				TCPreceivThread = new TCPPeerReceiver(s);
			    TCPreceivThread.start();
			}	
		}catch(Exception e){e.printStackTrace();}
	}
	
	//processa requisicoes
	public static void processRequest(String request) throws Exception {
		String splitRequest[] = request.split(", ");
		String filename, response="ERRO", action = splitRequest[0];
		Mensagem message;
		InetAddress IPAddress = InetAddress.getByName("localhost");

		switch(action) {
			case "JOIN":
				myFolder = splitRequest[1];    //readFiles(myFolder) -> "file1.txt; file2.txt"
				request  = action+", "+readFiles(myFolder)+", "+Integer.toString(myPort);
				message  = new Mensagem(request); //JOIN, path, 9696
				response = message.sendReceiveUDP(UDPcliSocket);
				
				if(response.contains("OK"))
					System.out.println("Sou peer ["+IPAddress.toString()+"]:["+myPort+"] com arquivos ["+readFiles(myFolder)+"]");
				break;
			case "SEARCH":
				filename = splitRequest[1];
				message  = new Mensagem(request);
				response = message.sendReceiveUDP(UDPcliSocket);
				System.out.println("peers com arquivo solicitado (IP "+IPAddress.toString()+"): ["+response.trim()+"]");
				break;
			case "DOWNLOAD":
				filename          =                  splitRequest[1];
				int otherPeerPort = Integer.parseInt(splitRequest[2]);
				message           = new Mensagem(request);
				TCPcliSocket      = new Socket("localhost",otherPeerPort);//make DOWNLOAD requests
				response = message.sendReceiveTCP(TCPcliSocket); //TCP
				if (response.contains("OK")) {
					saveFile(filename,TCPcliSocket);
					System.out.println("Arquivo ["+filename+"] baixado com sucesso na pasta ["+myFolder+"]");
					processRequest("UPDATE, "+myFolder+", "+myPort);
				}else{
					System.out.println("peer ["+IPAddress.toString()+"]:["+otherPeerPort+"] negou o download");
					//, pedindo agora para o peer ["+IPAddress.toString()+"]:[porta]")
				}
				break;
			case "UPDATE":
				myFolder = splitRequest[1];
				request  = action+", "+readFiles(myFolder)+", "+Integer.toString(myPort);//splitRequest[2];
				message  = new Mensagem(request);
				response = message.sendReceiveUDP(UDPcliSocket);				
				break;
			default: //"LEAVE"
				request = action+", "+myPort;
				message  = new Mensagem(request);
				response = message.sendReceiveUDP(UDPcliSocket);
		}
	}
	
	public static class UDPAliveReceiver extends Thread{
		public void run() {
			while(true) { //continuamente
				try {
					//espera receber um ALIVE do Server
					byte[] receiveData = new byte[1024];
					DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
					UDPservSocket.receive(receivedPacket);		
					String sentence = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
					int serverAlivePort = receivedPacket.getPort();
					InetAddress IPAddress = InetAddress.getByName("localhost");
					
					if(sentence.contains("ALIVE")) {
						byte[] sendData = new byte[sentence.length()];
						sendData = "ALIVE_OK".getBytes();
						DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, serverAlivePort);
						UDPservSocket.send(sendPacket);		//responde um ALIVE_OK				
					}

				}catch(Exception e) {e.printStackTrace();}
			}
		}
	}
	
	public static class UDPRequestsReceiver extends Thread{
		public void run() {
			while(true) { //processa requisicoes continuamente
				try {
					System.out.println("Faça uma requisição: ");
		    		String request = scan.nextLine();
		    		processRequest(request);
				}catch(Exception e) {e.printStackTrace();}
			}
		}
	}
	
	public static class TCPPeerReceiver extends Thread{
		public Socket s;
		public TCPPeerReceiver(Socket s) {this.s=s;}
		public void run() {
			try {
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String request = br.readLine();       //DOWNLOAD, arquivo1, 9696
                String file = request.split(", ")[1]; //teste1.txt
                
                String myfiles = readFiles(myFolder); //"comm.txt; output AVAoff2correto.txt"
                //checar se file está nesse Peer
                Boolean FileInThisPeer = myfiles.contains(file)? true:false;
                Boolean acceptDownload = false;
                
                if(FileInThisPeer)
                	acceptDownload = Math.random()>0.5 ? true:false;
                
                DataOutputStream output = new DataOutputStream(s.getOutputStream());

                if(acceptDownload) {
                	output.writeBytes("DOWNLOAD_OK\n");
                	System.out.println("executando sendFiles no detentor do arquivo");
                	sendFile(file,s);
                	System.out.println("terminou sendFiles");
                }else {
                	output.writeBytes("DOWNLOAD_NEGADO\n");
                }
                s.close(); 				
			}catch(Exception e) {e.printStackTrace();};
		}
	}

    //Função para ler todos os arquivos da pasta do peer e armazenar em uma String
    public static String readFiles(String path) {
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();
        String fileListString = "";

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    fileListString += file.getName() + "; ";
                }
            }
        }return fileListString;
    }
    
    //envia bytes de um arquivo sendo enviado para quem pediu DOWNLOAD
	public static void sendFile(String file, Socket s) throws IOException {
		DataOutputStream dos = new DataOutputStream(s.getOutputStream());
		FileInputStream fis = new FileInputStream(myFolder+"\\"+file);
		byte[] buffer = new byte[4096];
		
		while (fis.read(buffer) > 0) {
			dos.write(buffer);
		}
		fis.close();
		dos.close();	
	}
	
	//recebe bytes de um arquivo sendo baixado
	private static void saveFile(String file, Socket acceptSock) throws IOException {
		DataInputStream dis = new DataInputStream(acceptSock.getInputStream());
		FileOutputStream fos = new FileOutputStream(myFolder+"\\"+file);
		byte[] buffer = new byte[4096];		
		int read;
		
		//System.out.println("começou a transferir");
		while ((read=dis.read(buffer)) > 0) {
			fos.write(buffer,0,read);
		}//System.out.println("terminou de transferir");
		fos.close();
		dis.close();
	}
}