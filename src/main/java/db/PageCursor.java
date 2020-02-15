package main.java.db;

import java.util.ArrayList;

public class PageCursor<T> {
    private int docPerPage;
    private PageableQuery<T> query;

    private int page;

    public interface PageableQuery<S> {
        ArrayList<S> run(PageCursor<S> pc);
    }

    public PageCursor(int docPerPage, PageableQuery<T> query) {
        this.docPerPage = docPerPage;
        this.query = query;
    }

    public ArrayList<T> currentPage() {
        return query.run(this);
    }

    public ArrayList<T> nextPage() {
        page++;

        ArrayList<T> pageDocuments = query.run(this);
        if(pageDocuments.size() == 0)
            page--;

        return pageDocuments;
    }

    public ArrayList<T> prevPage() {
        if(page != 0)
            page--;

        return query.run(this);
    }

    public int getDocPerPage() { return docPerPage; }
    public int getPage() { return page; }
}
