import java.util.*;
import java.util.Random;

class Page {
    int pageNumber;
    boolean referenced;      // PG_referenced flag
    boolean dirty;          // PG_dirty flag
    boolean active;         // Whether page is in active_list
    long lastAccessTime;

    public Page(int pageNumber) {
        this.pageNumber = pageNumber;
        this.referenced = false;
        this.dirty = false;
        this.active = false;
        this.lastAccessTime = System.nanoTime();
    }

    @Override
    public String toString() {
        return pageNumber + (referenced ? "*" : "") + (active ? "A" : "I");
    }
}

// Linux 2.4 Two-List Page Replacement Algorithm
class LinuxPageReplacement {
    private final int totalCapacity; //number of pages the two lists can hold
    private LinkedList<Page> activeList;
    private LinkedList<Page> inactiveList;
    private Map<Integer, Page> pageMap;

    // Constants from Linux kernel
    private static final int SWAP_CLUSTER_MAX = 32;
    private static final int DEF_PRIORITY = 6;

    public LinuxPageReplacement(int capacity) {
        this.totalCapacity = capacity;
        this.activeList = new LinkedList<>();
        this.inactiveList = new LinkedList<>();
        this.pageMap = new HashMap<>();
    }

    //If the hashMap contains the page set the reference bit accordingly
    //Otherwise add the page to the inactive list
    public void accessPage(int pageNumber) {
        if (pageMap.containsKey(pageNumber)) {
            markPageAccessed(pageNumber);
        } else {
            handlePageFault(pageNumber);
        }
    }

    /*
  Mark accessed page has three conditions
  1. If the page is not active and is not already referenced --> set referenced to true
  2. If the page is not active, but has been referenced --> move it to the active list
  3. If the page is active and has been referenced --> do nothing
  */
    private void markPageAccessed(int pageNumber) {
        Page page = pageMap.get(pageNumber);
        page.lastAccessTime = System.nanoTime();

        if (!page.active && !page.referenced) {
            page.referenced = true;
            System.out.println("  → First reference to page " + pageNumber + " in inactive_list");
        } else if (!page.active && page.referenced) {
            activatePage(page);
            System.out.println("  → Second reference - promoting page " + pageNumber + " to active_list");
        } else if (page.active) {
            page.referenced = true;
            System.out.println("  → Accessing page " + pageNumber + " in active_list");
        }
    }


    //Moves a page from the inactive list and places it in the inactive list
    //clear the reference (linux does that)
    private void activatePage(Page page) {
        if (!page.active) {
            inactiveList.remove(page);
            activeList.addFirst(page);
            page.active = true;
            page.referenced = false;
        }
    }

    // If a page is accessed, but not currently in any of the lists it will
    // be added to the inactive list
    // If we cannot add anymore pages we will have to reclaim pages from the inactive list
    private void handlePageFault(int pageNumber) {
        Page newPage = new Page(pageNumber);

        if (pageMap.size() >= totalCapacity) {
            reclaimPages(1);
        }

        //Since inactive list is a FIFO buffer the new page will be added to the beginning
        // of the buffer
        inactiveList.addFirst(newPage);
        newPage.active = false;
        pageMap.put(pageNumber, newPage);
        System.out.println("  → Page fault: adding page " + pageNumber + " to inactive_list");

        // If there are too may active pages compared to inactive pages we will move the least active
        // pages to the inactive list
        refillInactive();
    }

    // This method calculates the number of pages to move from the active list to the inactive list
    // and moves those pages
    private void refillInactive() {
        //Calculates the number of pages needed to move
        int totalPages = activeList.size() + inactiveList.size();
        int targetInactiveSize = totalPages / 3;
        int pagesToMove = inactiveList.size() < targetInactiveSize ?
                (targetInactiveSize - inactiveList.size()) : 0;

        if (pagesToMove > 0 && !activeList.isEmpty()) {
            System.out.println("  → Refilling inactive_list: moving " + pagesToMove + " pages from active");
        }

        //While the active list is not empty keep removing pages until the number of pages
        // to move is zero
        for (int i = 0; i < pagesToMove && !activeList.isEmpty(); i++) {
            Page page = activeList.removeLast();

            //remove the last page in active list since its the least active page
            //If the page has been referenced recently move it to the start of the active list
            //and set its reference to false
            if (page.referenced) {
                page.referenced = false;
                activeList.addFirst(page);
                System.out.println("    • Page " + page.pageNumber + " referenced - rotating in active_list");
                i--;
            } else {
                // If the page has not been referenced recently then set its boolean values and add it to the
                //beginning of the inactive list
                page.active = false;
                page.referenced = true;
                inactiveList.addFirst(page);
                System.out.println("    • Moving page " + page.pageNumber + " to inactive_list");
            }
        }
    }

    // If there is not enough space in the inactive list then this method will be invoked
    // to remove the coldest pages
    private void reclaimPages(int nrPages) {
        System.out.println("  → Reclaiming " + nrPages + " page(s) from inactive_list");

        int freedPages = 0;
        int maxScan = Math.max(inactiveList.size() / DEF_PRIORITY, nrPages * 2);
        int scanned = 0;

        Iterator<Page> iterator = inactiveList.descendingIterator();
        List<Page> toRemove = new ArrayList<>();

        //Start scanning from the tail of the inactive list
        //Do not stop until we have freed enough pages or reached the scan limit or have run out of pages to scan
        while (iterator.hasNext() && freedPages < nrPages && scanned < maxScan) {
            Page page = iterator.next();
            scanned++;

            //If the page has not been referenced since being put in the inactive list
            //then add to the to remove list
            if (!page.referenced) {
                toRemove.add(page);
                freedPages++;
                System.out.println("    • Evicting page " + page.pageNumber + " from inactive_list");
            } else {
                // If the page has been referenced, set the reference to false have give it a second chance
                page.referenced = false;
                System.out.println("    • Page " + page.pageNumber + " referenced - giving second chance");
            }
        }

        // Remove all pages in the remove list
        for (Page page : toRemove) {
            inactiveList.remove(page);
            pageMap.remove(page.pageNumber);
        }


        // If there is still not enough space then keep doing the method
        if (freedPages < nrPages) {
            refillInactive();
        }
    }

    public void display() {
        System.out.println("Active: " + activeList.stream().map(Page::toString).toList() +
                " | Inactive: " + inactiveList.stream().map(Page::toString).toList());
    }

    public void displayStats() {
        double ratio = activeList.size() * 100.0 / (activeList.size() + inactiveList.size());
        System.out.println("\n=== Statistics ===");
        System.out.println("Active: " + activeList.size() + " pages (" + String.format("%.1f", ratio) + "%)");
        System.out.println("Inactive: " + inactiveList.size() + " pages (" + String.format("%.1f", 100-ratio) + "%)");
        System.out.println("Total: " + pageMap.size() + "/" + totalCapacity);
        System.out.println("Target ratio: ~67% active, ~33% inactive");
    }
}

// Main demonstration
public class Linux {
    public static void main(String[] args) {
        System.out.println("=== Linux Page Replacement Simulation ===\n");

        LinuxPageReplacement lru = new LinuxPageReplacement(8);

        Random rand = new Random();
        int[] accessSequence = new int[100];

        // Generate random sequence
        for (int i = 0; i < 100; i++) {
            accessSequence[i] = rand.nextInt(11); // 0 to 50 inclusive
        }

        /*
        int[] accessSequence = {
            1, 2, 3, 4,      // First access - all go to inactive
            1,               // Second access to 1 - promotes to active
            5, 6,            // New pages - go to inactive
            2,               // Second access to 2 - promotes to active
            7, 8, 9,         // More new pages - triggers eviction
            1, 2,            // Access hot pages in active
            10, 11,          // More new pages
            3, 4,            // Try to access old pages
            1, 2, 5          // Access existing pages
        };
        */

        System.out.println("--- Basic test ---");
        for (int page : accessSequence) {
            System.out.println("\nAccess " + page);
            lru.accessPage(page);
            lru.display();
        }

    }
}