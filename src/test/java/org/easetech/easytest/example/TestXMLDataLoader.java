
package org.easetech.easytest.example;

import org.easetech.easytest.annotation.DataLoader;
import org.easetech.easytest.annotation.Param;
import org.easetech.easytest.annotation.Report;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(org.easetech.easytest.runner.DataDrivenTestRunner.class)
@DataLoader(filePaths = { "input-data.xml" })
@Report
public class TestXMLDataLoader {
    
    

    @Test
    public Item getItemsDataFromXMLLoader(@Param( "libraryId")
    String libraryId, @Param( "itemId")
    String itemId, @Param( "itemType")
    String itemType, @Param( "expectedItems")
    String expectedItems) {
        System.out.print("Executing getItemsDataFromXMLLoader :");
        System.out.println("LibraryId :" + libraryId + " itemId : " + itemId + " itemType :" + itemType
            + " expectedItems :" + expectedItems);
        Item item=  new Item();
        item.setDescription("Description Modified");
        item.setItemId(itemId);
        item.setItemType(itemType);
        return item;
    }
    
    @Test
    @DataLoader(filePaths = { "classpath:input-data-mod.xml" })
    public Item getItemsDataFromXMLLoaderModified(@Param( "libraryId")
    String libraryId, @Param( "itemId")
    String itemId, @Param( "itemType")
    String itemType, @Param( "expectedItems")
    String expectedItems) {
        System.out.print("Executing getItemsDataFromXMLLoaderModified :");
        System.out.println("LibraryId :" + libraryId + " itemId : " + itemId + " itemType :" + itemType
            + " expectedItems :" + expectedItems);
        Item item=  new Item();
        item.setDescription("Description Modified");
        item.setItemId(itemId);
        item.setItemType(itemType);
        return item;
    }

}
