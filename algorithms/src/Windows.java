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

    public void markAccessed(){
        this.lastAccessTime = System.currentTimeMillis();
        this.referenced = true;
    }

    public String toString(){
        if(referenced){
            return pageNumber + "*";
        }
        return "" + pageNumber;
    }
}

class WorkingSet{
    int maxSize;
    long ageThreshold; // in ms
    Map<Integer, WindowPage> pages;
    int pageFaults;
    int totalAccesses;
    long lastReferenceClearTime;
    static final long REFERENCE_CLEAR_INTERVAL = 1000;

    public WorkingSet(int maxSize, long ageThreshold){
        this.maxSize = maxSize;
        this.ageThreshold = ageThreshold;
        this.pages = new HashMap<>();
        this.pageFaults = 0;
        this.totalAccesses = 0;
        this.lastReferenceClearTime = System.currentTimeMillis();
    }

    // If we are trying to access a page that is currently in the working set
    // then their reference bit will be set to true. Otherwise a page fault occurs
    public void accessPage(int pageNumber){
        totalAccesses++;

        clearStaleReferences(); //after a certain amount of time, set refrence boolean to false

        if(pages.containsKey(pageNumber)){
            WindowPage p = pages.get(pageNumber);
            p.markAccessed();
            System.out.println("Page " + pageNumber + " hit");
        }else{
            pageFaults++;
            handlePageFault(pageNumber);
        }

        trimWorkingSet(); // This will remove old pages that arent being referenced
    }

    // Clear reference bits periodically
    private void clearStaleReferences(){
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastReferenceClearTime > REFERENCE_CLEAR_INTERVAL){
            for(WindowPage p : pages.values()){
                p.referenced = false;
            }
            lastReferenceClearTime = currentTime;
            System.out.println("Cleared all reference bits");
        }
    }

    //If the last reference time is larger than the reference time threshold
    // set the reference bit for a page to false
    private void handlePageFault(int pageNumber){
        System.out.println("Page fault for page " + pageNumber);

        // If the working set is full then the algorithm will begin removing old pages in the set
        if(pages.size() >= maxSize){
            removeOldPages();
        }

        // When there is room a new page is created
        WindowPage p = new WindowPage(pageNumber);
        pages.put(pageNumber, p);
        System.out.println("Added page " + pageNumber);
    }

    // This method removes old pages from the working set
    private void removeOldPages(){
        long currentTime = System.currentTimeMillis();
        List<Integer> toRemove = new ArrayList<>();


        /*
        Look through all the pages in the working set. If a page has been idle
        for too long then add it tot the toRemove ArrayList
        */
        for(WindowPage p : pages.values()){
            long age = currentTime - p.lastAccessTime;
            if(age > ageThreshold && !p.referenced){
                toRemove.add(p.pageNumber);
            }
        }
        // If no pages are found remove the oldest page in the current working set
        if(toRemove.isEmpty()){
            removeOldestPage();
        }else{
            for(int num : toRemove){
                // Otherwise remove all the pages in the toRemove ArrayList
                pages.remove(num);
                System.out.println("Removed old page " + num);
            }
        }
    }

    //This method determines which page in the working set is the oldest
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
            pages.remove(oldest.pageNumber);
            System.out.println("Removed oldest page " + oldest.pageNumber);
        }
    }

    // Trim working set by removing unreferenced pages
    private void trimWorkingSet(){
        long currentTime = System.currentTimeMillis();
        List<Integer> toRemove = new ArrayList<>();
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
                    toRemove.add(p.pageNumber);
                }
            }
        }

        // remove pages
        for(int num : toRemove){
            pages.remove(num);
            System.out.println("Trimmed page " + num);
        }
    }

    public void printState(){
        System.out.println("\n=== Working Set State ===");
        System.out.println("Working Set: " + pages.keySet());
        System.out.println("Size: " + pages.size() + "/" + maxSize);

        long currentTime = System.currentTimeMillis();
        System.out.println("Page ages (ms):");
        for(WindowPage p : pages.values()){
            long age = currentTime - p.lastAccessTime;
            System.out.print("  Page " + p + ": " + age + "ms");
            if(p.referenced){
                System.out.print(" (referenced)");
            }
            System.out.println();
        }
    }

    public int getPageFaults(){return pageFaults;}
    public int getTotalAccesses(){return totalAccesses;}
}

public class Windows{
    public static void main(String[] args) throws InterruptedException{
        System.out.println("=== Windows Working Set Demo ===\n");

        WorkingSet ws = new WorkingSet(6, 1500);

        System.out.println("=== Phase 1: Build Working Set ===");
        int[] phase1 = {1, 2, 3, 1, 2, 4, 5};
        for(int page : phase1){
            System.out.println("\nAccess " + page);
            ws.accessPage(page);
            ws.printState();
            Thread.sleep(100);
        }

        System.out.println("\n=== Phase 2: Add More Pages ===");
        int[] phase2 = {6, 7, 7, 8, 9, 4, 5, 1, 5, 6, 2, 1, 8};
        for(int page : phase2){
            System.out.println("\nAccess " + page);
            ws.accessPage(page);
            ws.printState();
            Thread.sleep(150);
        }

        System.out.println("\n=== Phase 3: Random Accesses ===");
        Random rand = new Random();
        for(int i = 0; i < 15; i++){
            int page = rand.nextInt(8); // 0–7
            System.out.println("\nAccess " + page);
            ws.accessPage(page);
            ws.printState();
            Thread.sleep(80);
        }

        System.out.println("\nTotal accesses: " + ws.getTotalAccesses());
        System.out.println("Page faults: " + ws.getPageFaults());
    }
}
