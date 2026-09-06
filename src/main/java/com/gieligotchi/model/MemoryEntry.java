package com.gieligotchi.model;

public class MemoryEntry
{
	private long createdAt;
	private String title;
	private String detail;

	public MemoryEntry(String title, String detail)
	{
		this.createdAt = System.currentTimeMillis();
		this.title = title;
		this.detail = detail;
	}

	public long getCreatedAt() { return createdAt; }
	public String getTitle() { return title; }
	public String getDetail() { return detail; }
}
