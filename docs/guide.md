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
   1. [Adding Congregation Boundaries](#adding-congregation-boundaries)
   2. [Adding Territories](#adding-territories)
   3. [Importing City Suburb Boundaries (optional)](#importing-city-suburb-boundaries-optional)
4. [Printing Territory Cards](#printing-territory-cards)
5. [Advanced Topics](#advanced-topics)
    1. [Importing City Suburb Boundaries](#importing-city-suburb-boundaries)
    2. [Locating Staircase Entrances](#locating-staircase-entrances)


## Getting Started

1. Visit <https://beta.territorybro.com> and register a new congregation in Territory Bro. You will need to login using your Google or Facebook account.
   * If somebody has already registered your congregation to Territory Bro, they can give you access to it if you: 
     1. Visit <https://beta.territorybro.com/join> to find out your User ID code (it's made of 36 random numbers, letters and dashes).
     2. Send the User ID to the person who already can access your congregation in Territory Bro.
     3. They will need to visit the congregation's settings page in Territory Bro, and add you there using your User ID.

2. After registering the congregation, in the congregation's settings page there is a button to download a *QGIS project file*. Save the project file on your computer.

3. Download and install the latest [QGIS 3.x LTR (Long Term Release)](https://www.qgis.org/). This program will be used to edit the territory maps.

4. Use QGIS to open your congregation's QGIS project file (from step 2). You should see a world map, and you can zoom to your city. If you can't see the map, zoom all the way out using the **View \| Zoom Full** action from the application menus.

After this the next steps are to draw your congregation's boundaries on the map and then start drawing individual territories.


## Basic Concepts

After opening your congregation's project file in QGIS, you should see a *layers* panel. If it is not visible, select **View \| Panels \| Layers** from the application menus.

<img alt="Layers panel" src="layers-panel-1x.png" srcset="layers-panel-2x.png 2x">

On these layers you can draw *features*. A feature is what QGIS calls a shape and any *attributes* which describe the shape.

What the features on these layers mean is best understood by looking at a printed territory card:

<img alt="Territory card" src="territory-card-parts-1x.jpg" srcset="territory-card-parts-2x.jpg 2x" style="border: 1px solid #ccc;">

The shape of a feature on the **territory layer** is shown in the map as a red border ①, its **number attribute** is shown in the right corner ②, its **addresses attribute** is shown on the right side ③, and its **subregion attribute** is shown in the heading ④.

The minimap ⑤ is composed from multiple layers: The **territory layer**'s feature is shown as a dot. The **congregation_boundary layer**'s feature(s) are shown as a black border. The **subregion layer**'s feature which contains the current territory, is shown as a dark gray area.

The **card_minimap_viewport layer** determines the minimap's ⑤ zoom level. By default the whole **congregation_boundary** is shown in the minimap, but if the **card_minimap_viewport layer** contains features and the current territory is inside one of them, the minimap will zoom to that **card_minimap_viewport**.

> Only the **congregation_boundary** and **territory** layers are mandatory. The other layers can be left empty.

In the layers panel there are also various background maps. The default is **OpenStreetMap**, which has street maps for the whole world, but for some countries there are also additional maps. These same maps can be used for printing the territory cards inside Territory Bro.


## Editing the Maps

In QGIS, to draw a feature on one of the layers, first select the layer in the Layers panel and choose **Layer \| Toggle Editing** from the application menus.

Now the layer is editable and you can choose the **Edit \| Add Polygon Feature** tool. Left-click on the map to draw the polygon's corners. Right-click when you're finished, after which you can type the feature's attributes.

The feature is saved to the database only after you select **Layer \| Save Layer Edits**. To avoid losing work, it's best to save after each new territory you add, in case QGIS crashes and you need to restart QGIS (it is known to happen).

After you are done editing a layer, you can choose **Layer \| Toggle Editing** again to make the layer read-only.


### Adding Congregation Boundaries

> *This video is an unedited preview. Hopefully it will be of help. A proper tutorial video with some explanations will be published later.*

<iframe width="560" height="315" src="https://www.youtube.com/embed/48aRI8kir9Q" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>


### Adding Territories

> *This video is an unedited preview. Hopefully it will be of help. A proper tutorial video with some explanations will be published later.*

<iframe width="560" height="315" src="https://www.youtube.com/embed/WdT-uGJJbos" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>


### Importing City Suburb Boundaries (optional)

> *This video is an unedited preview. Hopefully it will be of help. A proper tutorial video with some explanations will be published later.*

<iframe width="560" height="315" src="https://www.youtube.com/embed/a3diEBgYEiw" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>


## Printing Territory Cards

> **Note: This video is a bit old, so you can skip the beginning and start watching from 4:00.** The export/import phase is no more needed, because you can now login to Territory Bro and it will show your territories directly. Also the experimental web browser features mentioned in the video are no more experimental.  

<iframe width="560" height="315" src="https://www.youtube.com/embed/WSxMMV6CpPg?list=PLSADDT9dzgRCEEopQhYLrdjVOfyfrC-Iz&start=239" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>

Log in to <https://beta.territorybro.com>, choose your congregation and to go printouts. You should see your territories ready to be printed using various card templates. You can print multiple territories by holding down `Ctrl` or `Shift` to select them from the territories list. 

By default, the background maps from OpenStreetMap are used, but Territory Bro can support also other freely available maps. [Create an issue](https://github.com/luontola/territory-bro/issues) if you know about a map you wish to use.

You can do minor adjustments to the maps: drag with mouse to move, scroll mouse wheel to zoom, hold `Alt+Shift` while dragging to rotate. These adjustments are not saved after you leave the page, so do them right before printing the cards. 

Using the latest Chrome or Firefox, save the territory cards page as PDF. Open the PDF in [Adobe Reader](https://get.adobe.com/reader/), print one page with 100% scale on paper (one A4 sheet fits 2 cards) and measure the distance between the printed crop marks. Then measure that what the dimensions of the card should really be, so that it would fit inside your protective plastic cases. Calculate the correct scale for printing the cards by dividing those two measures and try printing again.

Print the territory cards with the correct scale on thick paper. Cut the cards along the crop marks using a ruler and a sharp knife. Optionally cover the cards with adhesive book covering film (before cutting them out).

*TODO: Update this section of the user guide (the video).*


## Advanced Topics

### Importing City Suburb Boundaries

> **Note: This video is a bit old. It shows an older version of QGIS.** With QGIS 3 some things may work differently, and some things you might be able to do without installing plugins.

<iframe width="560" height="315" src="https://www.youtube.com/embed/19vtQn6CwEU?list=PLSADDT9dzgRCEEopQhYLrdjVOfyfrC-Iz" frameborder="0" allowfullscreen></iframe>

The above video shows starting at 4:45 that how to use QGIS to copy city suburb boundaries from a WFS service to your **subregion layer**. The video shows an old version of QGIS and Territory Bro, but the basic idea is the same.

*TODO: Update this section of the user guide (importing WFS/OSM/KML).*


### Locating Staircase Entrances

> **Note: This video is a bit old. It shows an older version of QGIS.** With QGIS 3 some things may work differently, and some things you might be able to do without installing plugins.

<iframe width="560" height="315" src="https://www.youtube.com/embed/Oj9Lt4W5F4c?list=PLSADDT9dzgRCEEopQhYLrdjVOfyfrC-Iz" frameborder="0" allowfullscreen></iframe>

The above video shows at time 1:05-3:40 that how to use [OpenStreetMap](https://www.openstreetmap.org/) to find out the staircase entrance letters/numbers easily. You could also use Google Street View.

*TODO: Update this section of the user guide.*
