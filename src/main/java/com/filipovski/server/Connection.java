package com.filipovski.server;

public class Connection {
	private Address clientAddress;
	private Address serverAddress;
	
	public Connection(Address clientAddress, Address serverAddress) {
		this.clientAddress = clientAddress;
		this.serverAddress = serverAddress;
	}

	public Connection(Address clientAddress) {
		this.clientAddress = clientAddress;
	}
	
	public Address getClientAddress() {
		return clientAddress;
	}

	public void setClientAddress(Address clientAddress) {
		this.clientAddress = clientAddress;
	}

	public Address getServerAddress() {
		return serverAddress;
	}

	public void setServerAddress(Address serverAddress) {
		this.serverAddress = serverAddress;
	}
}
