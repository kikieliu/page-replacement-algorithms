import java.util.*;

class MacPage{
    int number;
    boolean modified;
    long lastAccessTime;

    public MacPage(int number){
        this.number = number;
        this.modified = false;
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**************************************************************/
    /* Method: touch */
    /* Purpose: mark if page has been modified */
    /* Parameters: */
    /* boolean write: if we are trying to write */
    /* Returns: Boolean: did we find it? */
    /**************************************************************/
    public void touch(boolean write){
        this.lastAccessTime = System.currentTimeMillis();
        if(write){
            this.modified = true;
        }
    }

    /**************************************************************/
    /* Method: toString */
    /* Purpose: prints the page number and if its been modified */
    /* Parameters: */
    /* Returns: String */
    /**************************************************************/
    public String toString(){
        if(modified){
            return number + "m"; // m = modified
        }
        return "" + number;
    }
}

class MacOSVM{
    int maxPhysicalPages;
    int minFree;
    int targetFree;

    List<MacPage> active;
    List<MacPage> inactive;
    int freePages;

    Map<Integer, MacPage> pageTable; // which virtual page is in RAM

    int softFaults;  // inactive -> active
    int hardFaults;  // not resident at all

    // age thresholds
    long ACTIVE_TO_INACTIVE_AGE = 400;
    long INACTIVE_TO_FREE_AGE = 800;

    public MacOSVM(int maxPhysicalPages){
        this.maxPhysicalPages = maxPhysicalPages;

        this.minFree = maxPhysicalPages / 4;
        if(this.minFree < 1){
            this.minFree = 1;
        }
        this.targetFree = maxPhysicalPages / 2;
        if(this.targetFree < this.minFree){
            this.targetFree = this.minFree;
        }

        this.active = new ArrayList<>();
        this.inactive = new ArrayList<>();
        this.freePages = maxPhysicalPages;

        this.pageTable = new HashMap<>();
        this.softFaults = 0;
        this.hardFaults = 0;
    }

    /**************************************************************/
    /* Method: accessPage */
    /* Purpose: Determines if a soft fault or hard fault occurs and deals with */
    /* the page respectively*/
    /* Parameters: */
    /* int pageNumber: page we are trying to access */
    /* boolean write: if we are writing to a page*/
    /* Returns: Boolean: did we find it? */
    /**************************************************************/
    public void accessPage(int pageNumber, boolean write){
        System.out.print("Access page " + pageNumber);
        if(write){
            System.out.print(" (write)");
        }
        System.out.println();

        MacPage page = pageTable.get(pageNumber);

        // If the page is not null then we check two conditions
        // 1. If its in the inactive page then a soft fault occurs and the pages is
        // moved to the active page
        // 2. Otherwise the page is not in memory add it to the active list if Otherwise
        // is room
        if(page != null){
            // page is resident
            if(inactive.contains(page)){
                // soft fault: was in RAM but on inactive list
                softFaults++;
                inactive.remove(page);
                active.add(page);
                System.out.println("Soft fault: page " + pageNumber + " moved to active list");
            }else{
                System.out.println("Hit: page " + pageNumber + " already active");
            }
            page.touch(write);
        }else{
            // hard fault: page not in memory at all
            hardFaults++;
            System.out.println("Hard fault: page " + pageNumber + " not in memory");

            if(freePages == 0){
                //If there is no free pages then reclaim memory
                pageOutDaemon();
            }

            //If there are no pages after reclamation then start removing inactive pages
            if(freePages == 0 && !inactive.isEmpty()){
                MacPage victim = inactive.remove(0);
                pageTable.remove(victim.number);
                freePages++;
                System.out.println("Forced free of inactive page " + victim.number);
            }

            //If there is room then set the modified boolean value and add it to
            //the active list
            if(freePages > 0){
                freePages--;
                MacPage newPage = new MacPage(pageNumber);
                newPage.touch(write);
                active.add(newPage);
                pageTable.put(pageNumber, newPage);
                System.out.println("Loaded page " + pageNumber + " into active list");
            }else{
                System.out.println("No free pages available! (simulation limit)");
            }
        }

        // after each access try to rebalance the lists
        balanceQueues();
    }

    /**************************************************************/
    /* Method: balanceQueues */
    /* Purpose: Moves pages from active to inactive to keep sets balanced*/
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    private void balanceQueues(){
        moveOldActiveToInactive();

        //If there are less free pages then the threshold start removing pages
        if(freePages < minFree){
            pageOutDaemon();
        }
    }

    /**************************************************************/
    /* Method: moveOldActiveToInactive */
    /* Purpose: If its been too long since a page has been referenced on the active list */
    /* move it to the inactive list*/
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    private void moveOldActiveToInactive(){
        long now = System.currentTimeMillis();
        List<MacPage> toMove = new ArrayList<>();

        for(MacPage p : active){
            long age = now - p.lastAccessTime;
            if(age > ACTIVE_TO_INACTIVE_AGE){
                toMove.add(p);
            }
        }

        for(MacPage p : toMove){
            active.remove(p);
            inactive.add(p);
            System.out.println("Moved page " + p.number + " from active to inactive");
        }
    }

    /**************************************************************/
    /* Method: accessPage */
    /* Purpose: If there is not enough free pages then remove pages from the inactive list */
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/
    private void pageOutDaemon(){
        long now = System.currentTimeMillis();
        System.out.println("Page-out daemon running...");

        Iterator<MacPage> it = inactive.iterator();
        while(it.hasNext() && freePages < targetFree){
            MacPage p = it.next();
            long age = now - p.lastAccessTime;

            if(age > INACTIVE_TO_FREE_AGE){
                if(p.modified){
                    System.out.println("Paging out modified page " + p.number + " to disk");
                }else{
                    System.out.println("Dropping clean page " + p.number);
                }

                it.remove();
                pageTable.remove(p.number);
                freePages++;
            }
        }
    }

    public void printState(){
        System.out.println("Active: " + listPages(active));
        System.out.println("Inactive: " + listPages(inactive));
        System.out.println("Free pages: " + freePages);
        System.out.println("Soft faults: " + softFaults + ", Hard faults: " + hardFaults);
        System.out.println();
    }

    private String listPages(List<MacPage> list){
        List<String> names = new ArrayList<>();
        for(MacPage p : list){
            names.add(p.toString());
        }
        return names.toString();
    }
}

public class MacOS{
    public static void main(String[] args) throws InterruptedException{
        MacOSVM vm = new MacOSVM(5);

        int[] sequence = {1,2,3,4,1,2,5,6,1,2,3,4,5,6};
        Random r = new Random();

        for(int i = 0; i < sequence.length; i++){
            int page = sequence[i];
            boolean write = r.nextInt(3) == 0; // ~1/3 of accesses are writes
            vm.accessPage(page, write);
            vm.printState();
            Thread.sleep(150); // sleep so ages change over time
        }
    }
}
