Folder-Description:
===================
* `briar-*`: Specifically for the Briar app (Phone/Desktop/Headless)
* `bramble-*`: The protocol stack - not necessarily Briar-dependent

---

* `*-api`: public stuff that can be referenced from other packages and modules - mostly interfaces + a few utility classes
* `*-core`: implementations of api that are portable across Android/Desktop/Headless
* `*-java`: implementations of api that are specific to Desktop & Headless
