---
layout: default
title: User Guide
permalink: /guide/
---

# User Guide

## Table of Contents

1. [Getting Started](#getting-started)
2. [Basic Concepts](#basic-concepts)
3. [Editing the Maps](#editing-the-maps)
   1. [How to Create Congregation Boundaries](#how-to-create-congregation-boundaries)
   2. [How to Create and Edit Territories](#how-to-create-and-edit-territories)
   3. [How to Import City District Boundaries (optional)](#how-to-import-city-district-boundaries-optional)
4. [Printing Territory Cards](#printing-territory-cards)
5. [Tips](#tips)
    1. [Importing Map Data from KML/KMZ Files](#importing-map-data-from-kmlkmz-files)
    2. [Locating Staircase Entrances](#locating-staircase-entrances)


## Getting Started

1. Visit <https://beta.territorybro.com> and register a new congregation in Territory Bro. You will need to login using your Google or Facebook account.
   * If somebody has already registered your congregation to Territory Bro, they can give you access to it if you: 
     1. Visit <https://beta.territorybro.com/join> to find out your User ID code (it's made of 36 random numbers, letters and dashes).
     2. Send the User ID to the person who already can access your congregation in Territory Bro.
     3. They will need to visit the congregation's settings page in Territory Bro, and add you there using your User ID.

2. After registering the congregation, in the congregation's settings page there is a button to download a *QGIS project file*. Save the project file on your computer.

3. Download and install the latest [QGIS 3.x LTR (Long Term Release)](https://www.qgis.org/). This program will be used to edit the territory maps.

4. Use QGIS to open your congregation's QGIS project file (from step 2). You should see a world map, and you can zoom to your city. If you can't see the map, it could be because the map zoomed into a sea. Zoom all the way out using the **View \| Zoom Full** action from the application menus.

After this, the next steps are to draw your congregation's boundaries on the map, and then start drawing individual territories.


## Basic Concepts

After opening your congregation's project file in QGIS, you should see a *layers* panel. If it is not visible, select **View \| Panels \| Layers** from the application menus.

<img alt="Layers panel" src="layers-panel-1x.png" srcset="layers-panel-2x.png 2x">

On these layers you can draw *features*. A feature is what QGIS calls a shape and any *attributes* which describe the shape.

What the features on these layers mean is best understood by looking at a printed territory card:

<img alt="Territory card" src="territory-card-parts-1x.jpg" srcset="territory-card-parts-2x.jpg 2x" style="border: 1px solid #ccc;">

The shape of a feature on the **territory layer** is shown in the map as a red border ①, its **number attribute** is shown in the right corner ②, its **addresses attribute** is shown on the right side ③, and its **subregion attribute** is shown in the heading ④.

The minimap ⑤ is composed of multiple layers: The **territory layer**'s feature is shown as a dot. The **congregation_boundary layer**'s feature(s) are shown as a black border. The **subregion layer**'s feature which contains the current territory, is shown as a dark gray area.

The **card_minimap_viewport layer** determines the minimap's ⑤ zoom level. By default, the whole **congregation_boundary** is shown in the minimap, but if the **card_minimap_viewport layer** contains features and the current territory is inside one of them, the minimap will zoom to that **card_minimap_viewport**.

> Only the **congregation_boundary** and **territory** layers are mandatory. The other layers can be left empty.

In the layers panel there are also various background maps. The default is **OpenStreetMap**, which has street maps for the whole world, but for some countries there are also additional maps. These same maps can be used for printing the territory cards inside Territory Bro.


## Editing the Maps

In QGIS, to draw a feature on one of the layers, first select the layer in the Layers panel and choose **Layer \| Toggle Editing** from the application menus.

Now the layer is editable and you can choose the **Edit \| Add Polygon Feature** tool. Left-click on the map to draw the polygon's corners. Right-click when you're finished, after which you can type the feature's attributes.

The feature is saved to the database only after you select **Layer \| Save Layer Edits**. To avoid losing work, it's best to save after each new territory you add, in case QGIS crashes and you need to restart QGIS (it is known to happen).

After you are done editing a layer, you can choose **Layer \| Toggle Editing** again to make the layer read-only.


### How to Create Congregation Boundaries

<iframe width="560" height="315" src="https://www.youtube.com/embed/MBoxkZvLPqc" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>


### How to Create and Edit Territories

<iframe width="560" height="315" src="https://www.youtube.com/embed/yBPH5XU-NQo" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>


### How to Import City District Boundaries (optional)

<iframe width="560" height="315" src="https://www.youtube.com/embed/S0s3Y7IB9ho?si=yeBUyvWA6QlAHPDm" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>


## Printing Territory Cards

Log in to <https://beta.territorybro.com>, choose your congregation and to go **Printouts**. You should see your territories ready to be printed using various card templates. You can print multiple territories at once by holding down `Ctrl` or `Shift` to select them from the territories list. 

By default, the background maps from OpenStreetMap are used, but Territory Bro can support also other freely available maps. [Create an issue](https://github.com/luontola/territory-bro/issues) if you know about a map you wish to use. (Note that due to [license restrictions](https://github.com/luontola/territory-bro/issues/34#issuecomment-1709913100), Google Maps cannot be added there.)

You can do minor adjustments to the maps: drag with mouse to move, scroll mouse wheel to zoom, hold `Alt+Shift` while dragging to rotate. These adjustments are lost after you leave the page, so do them right before printing the cards. 

Do a test print with one page, and check if the territory cards come out the right size. You might need to adjust the print scale in your web browser's or printer's settings. If it doesn't allow specifying the scale with an exact percentage, you can first save the page as PDF, and then print the PDF using [Adobe Reader](https://get.adobe.com/reader/). Print one page with 100% scale on paper (one A4 sheet fits 2 cards) and measure the distance between the printed crop marks. Then measure that what the dimensions of the card should really be, so that it would fit inside your protective plastic cases. Calculate the correct scale for printing the cards by dividing those two measures and try printing again.

Print the territory cards with the correct scale on thick paper. Cut the cards along the crop marks using a ruler and a sharp knife. Optionally cover the cards with adhesive book covering film (best done before cutting them out).


## Tips

### Importing Map Data from KML/KMZ Files

If you've used Google Maps or Google Earth to store your territory data, you can export them as KML/KMZ files from there, and import them into QGIS. You can import pretty much any map data format to QGIS.

Drag and drop the map data file to QGIS, and QGIS will add it as a new layer. You can then copy the features from that layer and paste them to the territories layer.

To copy also the territory numbers and addresses, the field names must be the same in both layers. Right-click a layer and choose **Open Attribute Table** to check the names of the fields.

You can't edit a layer backed by a KML/KMZ file in QGIS, so you must make a copy of the layer to be able to rename the fields. Choose **Edit \| Select \| Select All Features** to select all features on the layer, copy them with **Edit \| Copy Features**, and paste them to a new layer with **Edit \| Paste Features As \| Temporary Scratch Layer**. Now you can enable editing the layer and rename the fields by right-clicking the layer and going to **Properties \| Fields**.


### Locating Staircase Entrances

Sometimes it's possible to use [OpenStreetMap](https://www.openstreetmap.org/) to find out the staircase entrance letters/numbers easily. On the right side of the map, there is a **Query features** tool (a mouse cursor with a question mark). Click the building with that, and it might show the staircase entrance in the nearby features list. You could also use Google Street View. That can be helpful in drawing the territory maps, so that you don't always need to visit the places physically.
