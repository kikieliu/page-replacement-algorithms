import java.util.*;

class WindowPage{
    int pageNumber;
    long lastAccessTime;
    boolean referenced;

    public WindowPage(int pageNumber){
        this.pageNumber = pageNumber;
        this.lastAccessTime = System.currentTimeMillis();
        this.referenced = true;
    }

    /**************************************************************/
    /* Method: markedAccess */
    /* Purpose: set the last time page was accessed and set reference to true */
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    public void markAccessed(){
        this.lastAccessTime = System.currentTimeMillis();
        this.referenced = true;
    }

    /**************************************************************/
    /* Method: toString */
    /* Purpose: print * if the page has been referenced */
    /* Parameters: */
    /* Returns: String */
    /**************************************************************/
    public String toString(){
        if(referenced){
            return pageNumber + "*";
        }
        return "" + pageNumber;
    }
}

class WorkingSet{
    int maxSize;
    long ageThreshold;
    Map<Integer, WindowPage> pages;
    int totalAccesses;
    long lastReferenceClearTime;
    static long REFERENCE_CLEAR_INTERVAL = 1000;

    public WorkingSet(int maxSize, long ageThreshold){
        this.maxSize = maxSize;
        this.ageThreshold = ageThreshold;
        this.pages = new HashMap<>();
        this.totalAccesses = 0;
        this.lastReferenceClearTime = System.currentTimeMillis();
    }

    /**************************************************************/
    /* Method: accessPage */
    /* Purpose: Set the pages reference to true, otherwise handle page fault */
    /* Parameters: */
    /* int pageNumber: page we are trying to access */
    /* Returns: void */
    /**************************************************************/
    public void accessPage(int pageNumber){
        totalAccesses++;

        clearReference(); //after a certain amount of time, set refrence boolean to false

        if(pages.containsKey(pageNumber)){
            WindowPage p = pages.get(pageNumber);
            p.markAccessed();
            System.out.println("  Page " + pageNumber + " hit in working set");
        }else{
            handlePageFault(pageNumber);
        }

        trimWorkingSet(); // This will remove old pages that arent being referenced
    }

    /**************************************************************/
    /* Method: clearReference */
    /* Purpose: After a certain time, if a page hasnt been referenced clear its */
    /* reference boolean */
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    private void clearReference(){
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastReferenceClearTime > REFERENCE_CLEAR_INTERVAL){
            for(WindowPage p : pages.values()){
                p.referenced = false;
            }
            lastReferenceClearTime = currentTime;
        }
    }

    /**************************************************************/
    /* Method: handlePageFault */
    /* Purpose: If there is enough space in the working set, add new page */
    /* Parameters: */
    /* int pageNumber: page number we are trying to access */
    /* Returns: void */
    /**************************************************************/
    private void handlePageFault(int pageNumber){
        System.out.println("  Page fault - adding page " + pageNumber);

        // If the working set is full then the algorithm will begin removing old pages in the set
        if(pages.size() >= maxSize){
            removeOldPages();
        }

        // When there is room a new page is created
        WindowPage p = new WindowPage(pageNumber);
        pages.put(pageNumber, p);
    }

    /**************************************************************/
    /* Method: search */
    /* Purpose: If its been too long since a page has been referenced, remove */
    /* page from working set */
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    private void removeOldPages(){
        long currentTime = System.currentTimeMillis();
        List<Integer> removePages = new ArrayList<>();
        
        /*
        Look through all the pages in the working set. If a page has been idle
        for too long then add it tot the removePages ArrayList
        */
        for(WindowPage p : pages.values()){
            long age = currentTime - p.lastAccessTime;
            if(age > ageThreshold && !p.referenced){
                System.out.println("  Page " + p.pageNumber + " removed from working set");
                removePages.add(p.pageNumber);
            }
        }
        // If no pages are found remove the oldest page in the current working set
        if(removePages.isEmpty()){
            removeOldestPage();
        }else{
            for(int num : removePages){
                // Otherwise remove all the pages in the removePages ArrayList
                pages.remove(num);
            }
        }
    }

    /**************************************************************/
    /* Method: removeOldestPage */
    /* Purpose: Finds oldest page in the working set and remove it  */
    /* to find a particular book */
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    private void removeOldestPage(){
        WindowPage oldest = null;
        long oldestTime = Long.MAX_VALUE;

        for(WindowPage p : pages.values()){
            if(p.lastAccessTime < oldestTime){
                oldestTime = p.lastAccessTime;
                oldest = p;
            }
        }

        if(oldest != null){
            System.out.println("  Page " + oldest.pageNumber + " removed from working set");
            pages.remove(oldest.pageNumber);
        }
    }

    /**************************************************************/
    /* Method: trimWorkingSet */
    /* Purpose: remove pages from the working set that are too old */
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    private void trimWorkingSet(){
        long currentTime = System.currentTimeMillis();
        List<Integer> removePages = new ArrayList<>();
        //Look through all pages in the working set
        for(WindowPage p : pages.values()){
            long age = currentTime - p.lastAccessTime;

            // If they are too old check if they have been reference
            if(age > ageThreshold){
                if(p.referenced){
                    // If they have been referenced give them a second chance
                    p.referenced = false;
                }else{
                    // Otherwise remove the page
                    System.out.println("  Page " + p.pageNumber + " removed from working set");
                    removePages.add(p.pageNumber);
                }
            }
        }

        // remove pages
        for(int num : removePages){
            pages.remove(num);
        }
    }

    public void print(){
        System.out.println("  Working Set: " + pages.values() + " | Size: " + pages.size() + "/" + maxSize);
    }
}

public class Windows{
    public static void main(String[] args) throws InterruptedException{
        System.out.println("=== Windows Working Set Page Management ===\n");

        WorkingSet ws = new WorkingSet(8, 1000);

        int[] sequence = {1, 2, 3, 4, 1, 2, 5, 6, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 9, 9, 8, 8, 7, 7, 6, 4, 4, 3, 3, 4, 2, 2, 5, 5,5, 2, 4, 1};
        //int[] sequence = {1,3,4,5,2,5,4,3,6,3,2,4,6,3,5,6,3};
        for(int page : sequence){
            System.out.println("Access page " + page);
            ws.accessPage(page);
            ws.print();
            System.out.println();

            //necessary so referenced bits can be cleared
            //If not present the program finishes too fast
            Thread.sleep(100);
        }
    }
}