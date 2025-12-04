import java.util.*;

class LinuxPage {
    int pageNumber;
    boolean referenced;
    boolean dirty;
    boolean active;
    long lastAccessTime;

    public LinuxPage(int pageNumber) {
        this.pageNumber = pageNumber;
        this.referenced = false;
        this.dirty = false;
        this.active = false;
        this.lastAccessTime = System.nanoTime();
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

// Linux 2.4 Two-List Page Replacement Algorithm
class LinuxPageReplacement {
    private final int totalCapacity; //number of pages the two lists can hold
    private LinkedList<LinuxPage> activeList;
    private LinkedList<LinuxPage> inactiveList;
    private Map<Integer, LinuxPage> pageMap;

    // Constants from Linux kernel
    private static final int SWAP_CLUSTER_MAX = 32;
    private static final int DEF_PRIORITY = 6;

    public LinuxPageReplacement(int capacity) {
        this.totalCapacity = capacity;
        this.activeList = new LinkedList<>();
        this.inactiveList = new LinkedList<>();
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
            markPageAccessed(pageNumber);
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
    private void markPageAccessed(int pageNumber) {
        LinuxPage page = pageMap.get(pageNumber);
        page.lastAccessTime = System.nanoTime();

        if (!page.active && !page.referenced) {
            page.referenced = true;
            System.out.println("  First reference to page " + pageNumber + " in inactive list");
        } else if (!page.active && page.referenced) {
            activatePage(page);
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
    private void activatePage(LinuxPage page) {
        if (!page.active) {
            inactiveList.remove(page);
            activeList.addFirst(page);
            page.active = true;
            page.referenced = false;
        }
    }

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
        inactiveList.addFirst(newPage);
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
            LinuxPage page = activeList.removeLast();

            //remove the last page in active list since its the least active page
            //If the page has been referenced recently move it to the start of the active list
            //and set its reference to false
            if (page.referenced) {
                page.referenced = false;
                activeList.addFirst(page);
                i--;
            } else {
                // If the page has not been referenced recently then set its boolean values and add it to the
                //beginning of the inactive list
                page.active = false;
                //page.referenced = false;
                inactiveList.addFirst(page);
            }
        }
    }

    /**************************************************************/
    /* Method: reclaimPages */
    /* Purpose: If there is not enough room in the inactive list then remove */
    /* pages from the inactive list  */
    /* Parameters: */
    /* int nrPages: number of pages to remove */
    /* Returns: void */
    /**************************************************************/
    private void reclaimPages(int nrPages) {
        System.out.println("  Reclaiming " + nrPages + " page(s) from inactive list");
        int remaining = nrPages;
        while(remaining > 0 && (!inactiveList.isEmpty() || !activeList.isEmpty())) {
            int freedPages = 0;
            int maxScan = Math.max(inactiveList.size() / DEF_PRIORITY, nrPages * 2);
            int scanned = 0;

            Iterator<LinuxPage> iterator = inactiveList.descendingIterator();
            List<LinuxPage> toRemove = new ArrayList<>();

            //Start scanning from the tail of the inactive list
            //Do not stop until we have freed enough pages or reached the scan limit or have run out of pages to scan
            while (iterator.hasNext() && freedPages < remaining && scanned < maxScan) {
                LinuxPage page = iterator.next();
                scanned++;

                //If the page has not been referenced since being put in the inactive list
                //then add to the to remove list
                if (!page.referenced) {
                    toRemove.add(page);
                    freedPages++;
                } else {
                    // If the page has been referenced, set the reference to false have give it a second chance
                    page.referenced = false;
                }
            }
            // Remove all pages in the remove list
            for (LinuxPage page : toRemove) {
                inactiveList.remove(page);
                pageMap.remove(page.pageNumber);
            }
            remaining -= freedPages;

            if (remaining > 0) {
                if(activeList.isEmpty()) break;
                refillInactive();
            }
        }
    }

    public void display() {
        System.out.println("  Active: " + activeList.stream().map(LinuxPage::toString).toList() +
                " | Inactive: " + inactiveList.stream().map(LinuxPage::toString).toList());
    }
}

// Main demonstration
public class Linux {
    public static void main(String[] args) {
        System.out.println("=== Linux Two-List Page Replacement ===\n");

        LinuxPageReplacement lru = new LinuxPageReplacement(5);

        int[] sequence = {1, 2, 3, 4, 1, 2, 5, 6, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 9, 9, 8, 8, 7, 7, 6, 4, 4, 3, 3, 4, 2, 2, 5, 5,5, 2, 4, 1};

        for (int page : sequence) {
            System.out.println("Access page " + page);
            lru.accessPage(page);
            lru.display();
            System.out.println();
        }
    }
}