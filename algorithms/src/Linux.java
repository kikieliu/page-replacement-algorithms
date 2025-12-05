import java.util.*;

class LinuxPage {
    int pageNumber;
    boolean referenced;
    boolean active;
    //boolean dirty;
    //long lastAccessTime;

    public LinuxPage(int pageNumber) {
        this.pageNumber = pageNumber;
        this.referenced = false;
        this.active = false;
        //this.dirty = false;
        //this.lastAccessTime = System.nanoTime();
    }

    /**************************************************************/
    /* Method: toString */
    /* Purpose: Print out if the page has been referenced and is active */
    /* Parameters: */
    /* Returns: String */
    /**************************************************************/
    @Override
    public String toString() {
        return pageNumber + (referenced ? "*" : "");
    }
}

class LinuxPageReplacement {
    private int totalCapacity; //number of pages the two lists can hold
    private ArrayList<LinuxPage> activeList;
    private ArrayList<LinuxPage> inactiveList;
    private Map<Integer, LinuxPage> pageMap;

    public LinuxPageReplacement(int capacity) {
        this.totalCapacity = capacity;
        this.activeList = new ArrayList<>();
        this.inactiveList = new ArrayList<>();
        this.pageMap = new HashMap<>();
    }

    /**************************************************************/
    /* Method: accessPage */
    /* Purpose: Find if a page is in the pageMap, if not handle the page fault */
    /* Parameters: */
    /* int pageNumber: page number to access */
    /* Returns: void */
    /**************************************************************/
    public void accessPage(int pageNumber) {
        if (pageMap.containsKey(pageNumber)) {
            markAccessed(pageNumber);
        } else {
            handlePageFault(pageNumber);
        }
    }

    /**************************************************************/
    /* Method: markPageAccessed */
    /* Purpose: If page is in the inactive list and has not been referenced set reference to true */
    /* If page is in the inactive list and has been referenced, move to active list */
    /* If page is active set referenced to true*/
    /* Parameters: */
    /* int pageNumber: page number we want to access */
    /* Returns: void */
    /**************************************************************/
    private void markAccessed(int pageNumber) {
        LinuxPage page = pageMap.get(pageNumber);
        //page.lastAccessTime = System.nanoTime();

        if (!page.active && !page.referenced) {
            page.referenced = true;
            System.out.println("  First reference to page " + pageNumber + " in inactive list");
        } else if (!page.active && page.referenced) {
            //activatePage(page);
            inactiveList.remove(page);
            activeList.add(page);
            page.active = true;
            page.referenced = false;
            System.out.println("  Second reference - promoting page " + pageNumber + " to active list");
        } else if (page.active) {
            page.referenced = true;
            System.out.println("  Page " + pageNumber + " hit in active list");
        }
    }
    
    /**************************************************************/
    /* Method: activatePage */
    /* Purpose: Moves a page from the inactive list to active list */
    /* Parameters: */
    /* LinuxPage page: Page object we cant to move */
    /* Returns: void */
    /**************************************************************/
    /*
    private void activatePage(LinuxPage page) {
        if (!page.active) {
            inactiveList.remove(page);
            activeList.add(page);
            page.active = true;
            page.referenced = false;
        }
    }
    */

    /**************************************************************/
    /* Method: handlePageFault */
    /* Purpose: Handles what to do with the page when a page that is not in the */
    /* map is trying to get accessed */
    /* Parameters: */
    /* int pageNumber: page number we are trying to add */
    /* Returns: void */
    /**************************************************************/
    private void handlePageFault(int pageNumber) {
        LinuxPage newPage = new LinuxPage(pageNumber);

        if (pageMap.size() >= totalCapacity && !inactiveList.isEmpty()) {
            reclaimPages(1);
        }

        //Since inactive list is a FIFO buffer the new page will be added to the beginning
        // of the buffer
        inactiveList.add(newPage);
        newPage.active = false;
        pageMap.put(pageNumber, newPage);
        System.out.println("  Page fault - adding page " + pageNumber + " to inactive list");

        // removes pages that have been inactive for too long
        // pages to the inactive list
        refillInactive();
    }

    /**************************************************************/
    /* Method: refillInactive */
    /* Purpose: If there are too many pages in the active list then this will */
    /* start moving pages to the inactive list */
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    private void refillInactive() {
        //Calculates the number of pages needed to move
        int totalPages = activeList.size() + inactiveList.size();
        int targetInactiveSize = totalPages / 3;
        int pagesToMove = inactiveList.size() < targetInactiveSize ?
                (targetInactiveSize - inactiveList.size()) : 0;

        if (pagesToMove > 0 && !activeList.isEmpty()) {
            System.out.println("  Refilling inactive list: moving " + pagesToMove + " pages from active");
        }

        //While the active list is not empty keep removing pages until the number of pages
        // to move is zero
        for (int i = 0; i < pagesToMove && !activeList.isEmpty(); i++) {
            LinuxPage page = activeList.remove(activeList.size() - 1);

            //remove the last page in active list since its the least active page
            //If the page has been referenced recently move it to the start of the active list
            //and set its reference to false
            if (page.referenced) {
                page.referenced = false;
                activeList.add(page);
                i--;
            } else {
                // If the page has not been referenced recently then set its boolean values and add it to the
                //beginning of the inactive list
                page.active = false;
                //page.referenced = false;
                inactiveList.add(page);
            }
        }
    }

    /**************************************************************/
    /* Method: reclaimPages */
    /* Purpose: If there is not enough room in the inactive list then remove */
    /* pages from the inactive list  */
    /* Parameters: */
    /* int numPages: number of pages to remove */
    /* Returns: void */
    /**************************************************************/
    private void reclaimPages(int numPages) {
        System.out.println("  Reclaiming page(s) from inactive list");
        int remaining = numPages;
        while(remaining > 0 && (!inactiveList.isEmpty() || !activeList.isEmpty())) {
            int freedPages = 0;
            int maxScan = Math.max(inactiveList.size() / 6, numPages * 2);
            int scanned = 0;

            List<LinuxPage> removePage = new ArrayList<>();
            List<LinuxPage> secondChance = new ArrayList<>();

            //Start scanning from the tail of the inactive list
            //Do not stop until we have freed enough pages or reached the scan limit or have run out of pages to scan
             for(int i = inactiveList.size() - 1; i >= 0 && freedPages < remaining && scanned < maxScan; i--) {
                LinuxPage page = inactiveList.get(i);
                scanned++;

                //If the page has not been referenced since being put in the inactive list
                //then add to the to remove list
                if (!page.referenced) {
                    removePage.add(page);
                    freedPages++;
                } else {
                    // If the page has been referenced, set the reference to false have give it a second chance
                    page.referenced = false;
                    secondChance.add(page);
                }
            }
            // Remove all pages in the remove list
            for (LinuxPage page : removePage) {
                inactiveList.remove(page);
                System.out.println("  Removing page " + page);
                pageMap.remove(page.pageNumber);
            }
            //give referenced pages a second change
            for (LinuxPage page : secondChance) {
                inactiveList.remove(page);
                System.out.println("  Giving page " + page + " a second chance");
                activeList.add(page);
                page.active = true;
            }
            remaining -= freedPages;

            if (remaining > 0 && !activeList.isEmpty()) {
                //if(activeList.isEmpty()) break;
                refillInactive();
            }
        }
    }

    public void display() {
        System.out.println("  Active: " + activeList +
                " | Inactive: " + inactiveList);
    }
}

// Main demonstration
public class Linux {
    public static void main(String[] args) {
        LinuxPageReplacement lru = new LinuxPageReplacement(5);
        System.out.println("=== Linux Page Replacement Test ===\n");
        System.out.println("== Adding Pages to Inactive ===\n");
        int[] pageSequence1 = {1,2,3,4,5};
        for(int page: pageSequence1){
            System.out.println("Access Page: " + page);
            lru.accessPage(page);
            lru.display();
            System.out.println();
        }

        System.out.println("== Referencing Inactive Pages ===\n");
        int[] pageSequence2 = {2,3,5};
        for(int page: pageSequence2){
            System.out.println("Access Page: " + page);
            lru.accessPage(page);
            lru.display();
            System.out.println();
        }

        System.out.println("== Testing Inactive Reclaiming ===\n");
        int[] pageSequence3 = {6,8,9};
        for(int page: pageSequence3){
            System.out.println("Access Page: " + page);
            lru.accessPage(page);
            lru.display();
            System.out.println();
        }

        System.out.println("== Testing Inactive to Active ===\n");
        int[] pageSequence4 = {9,8,9,8};
        for(int page: pageSequence4){
            System.out.println("Access Page: " + page);
            lru.accessPage(page);
            lru.display();
            System.out.println();
        }

        System.out.println("== Testing Inactive Refill ===\n");
        int[] pageSequence5 = {1, 1, 4, 4, 7, 7};
        for(int page: pageSequence5){
            System.out.println("Access Page: " + page);
            lru.accessPage(page);
            lru.display();
            System.out.println();
        }
    }
}