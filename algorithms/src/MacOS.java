import java.util.*;

class MacPage{
    int pageNumber;
    boolean modified;
    long lastAccessTime;

    public MacPage(int pageNumber){
        this.pageNumber = pageNumber;
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
    /* Purpose: prints the page pageNumber and if its been modified */
    /* Parameters: */
    /* Returns: String */
    /**************************************************************/
    public String toString(){
        if(modified){
            return pageNumber + "m"; // m = modified
        }
        return "" + pageNumber;
    }
}

class MacPageReplacement{
    int maxPhysicalPages;
    int minFree;
    int targetFree;

    List<MacPage> active;
    List<MacPage> inactive;
    int freePages;

    Map<Integer, MacPage> pageTable; // which virtual page is in RAM

    // age thresholds
    long activeThreshold = 400;
    long inactiveThreshold = 800;

    public MacPageReplacement(int maxPhysicalPages){
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
            if(inactive.contains(page)){
                // soft fault: was in RAM but on active list
                inactive.remove(page);
                active.add(page);
                System.out.println("    Soft fault: page " + pageNumber + " moved to active list");
            }else{
                System.out.println("    Hit: page " + pageNumber + " already active");
            }
            page.touch(write);
        }else{
            // hard fault: page not in memory at all
            System.out.println("    Hard fault: page " + pageNumber + " not in memory");

            if(freePages == 0){
                //If there is no free pages then reclaim memory
                pageOutDaemon();
            }

            //If there is room then set the modified boolean value and add it to
            //the active list
            if(freePages > 0){
                freePages--;
                MacPage newPage = new MacPage(pageNumber);
                newPage.touch(write);
                active.add(newPage);
                pageTable.put(pageNumber, newPage);
                System.out.println("    Load page " + pageNumber + " into active list");
            }else{
                System.out.println("    No free pages available!");
            }
        }

        // after each access try to rebalance the lists
        //Since we set maxPages to 5 this isnt necessary since its a small number
        moveOldActiveToInactive();
        //balanceQueues();
    }

    /**************************************************************/
    /* Method: balanceQueues */
    /* Purpose: Moves pages from active to inactive to keep sets balanced*/
    /* Parameters: */
    /* Returns: void */
    /**************************************************************/

    /*
    private void balanceQueues(){
        moveOldActiveToInactive();

        //If there are less free pages then the threshold start removing pages
        if(freePages < minFree){
            pageOutDaemon();
        }
    }
    */





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
            if(age > activeThreshold){
                toMove.add(p);
            }
        }

        for(MacPage p : toMove){
            active.remove(p);
            inactive.add(p);
            System.out.println("    Moved page " + p.pageNumber + " from active to inactive");
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
        System.out.println("    Running Page-out Daemon");
        boolean urgent = false;
        if(freePages == 0){
            urgent = true;
        }

        List<MacPage> pagesToRemove = new ArrayList<>();

        for(MacPage p : inactive){
            if(freePages >= targetFree){
                break;
            }

            long age = now - p.lastAccessTime;

            if(age > inactiveThreshold || urgent){
                if(p.modified){
                    System.out.println("    Paging out modified page " + p.pageNumber + " to disk");
                }else{
                    System.out.println("    Dropping clean page " + p.pageNumber);
                }

                pagesToRemove.add(p);
                pageTable.remove(p.pageNumber);
                freePages++;
            }
        }

        inactive.removeAll(pagesToRemove);
    }

    public void printState(){
        System.out.println("Active: " + listPages(active));
        System.out.println("Inactive: " + listPages(inactive));
        System.out.println("Free pages: " + freePages);
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
        MacPageReplacement vm = new MacPageReplacement(5);
        System.out.println("MacOS Page Replacement\n");


        System.out.println("Adding Pages to RAM\n");
        int[] sequence = {1, 2, 3, 1, 4};
        for(int i = 0; i < sequence.length; i++){
            int page = sequence[i];
            boolean write = false; //Write does not affect the algorithm, just set it to false so its easier to read
            vm.accessPage(page, write);
            vm.printState();
            Thread.sleep(100); // sleep so ages change over time
        }

        System.out.println("Testing Page Out Daemon\n");
        int[] sequence1 = {7,8,9};
        for(int i = 0; i < sequence1.length; i++){
            int page = sequence1[i];
            boolean write = false; //Write does not affect the algorithm, just set it to false so its easier to read
            vm.accessPage(page, write);
            vm.printState();
            Thread.sleep(100); // sleep so ages change over time
        }

        System.out.println("Active to Inactive\n");
        for(int i = 0; i < 3; i++){
            int page = 1;
            boolean write = false; //Write does not affect the algorithm, just set it to false so its easier to read
            vm.accessPage(page, write);
            vm.printState();
            Thread.sleep(100); // sleep so ages change over time
        }
    }
}
