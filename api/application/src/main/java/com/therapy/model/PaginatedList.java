package com.therapy.model;

import java.util.List;

public class PaginatedList<T> {
    private List<T> items;
    private int total;
    private int page;
    private int pageSize;
    private String nextToken;

    public PaginatedList(List<T> items, int page, int pageSize, String nextToken) {
        this.items = items;
        this.total = items.size();
        this.page = page;
        this.pageSize = pageSize;
        this.nextToken = nextToken;
    }

    public List<T> getItems() { return items; }
    public int getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public String getNextToken() { return nextToken; }
}
