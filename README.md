# Rokustic
This repository contains the source code for the Rokustic tool developed for, and used in, the paper ["The TV is Smart and Full of Trackers: Measuring Smart TV Advertising and Tracking"](https://petsymposium.org/2020/files/papers/issue2/popets-2020-0021.pdf). Rokustic enables automated installation and interaction with Roku apps (channels) while logging the Roku device's network traffic. For a more detailed description of Rokustic (and Firetastic, Rokustic's sibling tool for Amazon Fire TV), please refer to the aforementioned paper and [the project website](https://athinagroup.eng.uci.edu/projects/smarttv/).

# Citation
If you create a publication (including web pages, papers published by a third party, and publicly available presentations) using Rokustic and/or [the accompanying smart TV dataset](https://athinagroup.eng.uci.edu/projects/smarttv/data/), please cite the corresponding paper as follows:
```
@article{varmarken2020smarttv,
  title={{The TV is Smart and Full of Trackers: Measuring Smart TV Advertising and Tracking}},
  author={Varmarken, Janus and Le, Hieu and Shuba, Anastasia and Markopoulou, Athina and Shafiq, Zubair},
  journal={Proceedings on Privacy Enhancing Technologies},
  volume={2020},
  number={2},
  year={2020},
  publisher={De Gruyter Open}
}
```
We also encourage you to provide us (smarttv.uci@gmail.com) with a link to your publication. We use this information in reports to our funding agencies.

# Dependencies
Rokustic uses [Pcap4J](https://github.com/kaitoy/pcap4j) to capture network traffic and thus inherits [the platform requirements of Pcap4J](https://github.com/kaitoy/pcap4j#how-to-use) (in particular, the availability of a pcap native library). Gradle (Maven) will handle inclusion of the Pcap4J library itself (and additional libraries used by Rokustic) automatically when you build/run Rokustic using the provided Gradle Wrapper.

# Hardware setup
Rokustic is designed to run on a (UNIX-based) machine that acts as a wireless access point (AP) and a gateway for devices connected to this AP. To eliminate the need for filtering the collected network traces (by IP of the Roku device), we recommend that you do *not* connect any other devices than the Roku itself to this AP. To start/stop Rokustic, set up the machine to enable SSH login on its wired interface. When you start Rokustic and tell it to log traffic on the wireless interface (to which the Roku is connected), any management (SSH) traffic on the wired interface will not become part of the collected network traces. We use a Raspberry Pi 3 Model B as the AP/gateway (depicted in the figure below), but any (UNIX-based) machine with a wireless and a wired interface should suffice.

![A diagram depicting the hardware setup we use for Rokustic. A Raspberry Pi 3 Model B is set up as a wireless access point and gateway. The Roku is connected to this wireless access point.](https://github.com/UCI-Networking-Group/rokustic/blob/master/images/rokustic-hardware-setup.png "Rokustic hardware setup")

## Raspberry Pi setup
To configure a Raspberry Pi 3 Model B (or any other model with both a wired and a wireless network interface) to run Rokustic, follow the instructions below.

- [First set up the Raspberry Pi as a wireless router (AP and gateway with DHCP server and NAT)](https://www.raspberrypi.org/documentation/configuration/wireless/access-point-routed.md).
- [(Optional) Perform basic security configuration.](https://www.raspberrypi.org/documentation/configuration/security.md)
- [(Optional) Enable SSH for remote access to the Pi.](https://www.raspberrypi.org/documentation/remote-access/ssh/)

Finally, you should make sure that routing is set up correctly on the Raspberry Pi. If set up using the tutorials linked above, the Raspberry Pi's `eth0` should become the default gateway (in the view of the Pi itself). Use `$ ip route show` to verify the default gateway. You should see an output line similar to `default via A.B.C.D dev eth0 ...` where `A.B.C.D` is the IP address of your Pi's `eth0` interface.

As Rokustic uses SSDP to discover Rokus connected to the wireless network hosted by the Pi, you also need to add a routing entry that makes the Pi send SSDP queries out on its `wlan0` interface rather than its `eth0` interface:
- Add the route using `$ sudo ip route add 239.255.255.250 dev wlan0` (239.255.255.250 is the multicast address used for the SSDP queries).
- Then verify it using `$ ip route show`. You should see a line in the output similar to `239.255.255.250 dev wlan0 scope link`. 


# Usage
You run Rokustic using the command `sudo ./gradlew run` from the root of this repository (note: `sudo` is needed as the program starts and stops a packet capture behind the scenes). Rokustic will first perform an SSDP scan to discover Rokus on the local network, and then prompt you to select your target Roku. Next, you will be asked if you would like to install Roku apps, or perform automated interaction with the set of apps currently installed on the Roku device.

## Installing apps
If you select this option, you will be prompted for a path to a file that specifies what channels to install. The file format is simple: one app ID (an integer) per line, and nothing else. For example, a file with the following content will make Rokustic install apps with IDs 1, 4, and 9 on the target Roku device:

```
1
4
9
```
Rokustic will ignore lines starting with a pound sign (`#`), enabling you to add comments to these app list files.

## Automatically interact with Roku apps while logging network traffic
If you select this option, you will be prompted for the name of the network interface that is to be the target of the packet capture. This will typically be `wlan0` if you run Rokustic on a Raspberry Pi that acts as an access point that the Roku connects to.

After specifying the network interface, you will also be prompted for an output directory. The network traces captured during interaction with each app will be written to this output directory. There will be one network trace per app. The naming convention used for the network trace files is `app-<ID>.pcap` where `<ID>` is the ID of the app that was being automatically interacted with while the network trace was captured.

# Scripts
The `scripts` directory contains scripts that are related to Rokustic, but which are to be run as separate, standalone components. All scripts are written in Python 3. Dependencies (can be installed using `pip`): `requests`, `unicodecsv`.

The Roku Channel Store crawlers were written for version 6 of the API backing the Roku Channel Store and were last verified as operational in early August 2020. Note that the scripts target the US version of the Roku Channel Store.

## Crawling the Roku Channel Store for available channels
The [`roku_channelstore_crawler.py`](/scripts/roku_channelstore_crawler.py) script determines all currently available (free) Roku apps by crawling the [Roku Channel Store](https://channelstore.roku.com/). It expects a single argument, which specifies where to write its output:

```
$ python3 roku_channelstore_crawler.py /home/rokustic/channelstore_crawl.csv
```

The script outputs a CSV file that maps each channel to its category/categories (a Roku channel can be part of multiple channel categories). The columns in the output CSV are: `[category_id, category_name, category_type, chanenl_id, channel_name, channel_price]`. Most columns should be self-explanatory. The `category_type` specifies how the category is maintained by Roku. Known possible values (as of August 2020) are `curated` and `tag`. A few lines from an August 2020 crawl are provided below as an example output:
```
category_id,category_name,category_type,chanenl_id,channel_name,channel_price
F73EBEF3-F4A9-484F-9D33-A1420107E170,Featured,curated,140474,AT&T TV,0
F73EBEF3-F4A9-484F-9D33-A1420107E170,Featured,curated,32614,HappyKids.tv,0
F73EBEF3-F4A9-484F-9D33-A1420107E170,Featured,curated,551012,Apple TV,0
F73EBEF3-F4A9-484F-9D33-A1420107E170,Featured,curated,71845,NewsON,0
F73EBEF3-F4A9-484F-9D33-A1420107E170,Featured,curated,251088,STIRR - the new free TV,0
...
D673A62A-2891-4683-BB02-A4DC00E26BC9,4K Editor's Picks,curated,12,Netflix,0
D673A62A-2891-4683-BB02-A4DC00E26BC9,4K Editor's Picks,curated,551012,Apple TV,0
D673A62A-2891-4683-BB02-A4DC00E26BC9,4K Editor's Picks,curated,291097,Disney Plus,0
D673A62A-2891-4683-BB02-A4DC00E26BC9,4K Editor's Picks,curated,43465,fuboTV Watch Live Sports & TV,0
D673A62A-2891-4683-BB02-A4DC00E26BC9,4K Editor's Picks,curated,61657,CuriosityStream,0
...
52E61EAB-1170-4E32-AA15-A6A200EFD57A,New & Notable,curated,592506,Peloton - at home fitness,0
52E61EAB-1170-4E32-AA15-A6A200EFD57A,New & Notable,curated,291097,Disney Plus,0
52E61EAB-1170-4E32-AA15-A6A200EFD57A,New & Notable,curated,551012,Apple TV,0
52E61EAB-1170-4E32-AA15-A6A200EFD57A,New & Notable,curated,1508,NBA,0
52E61EAB-1170-4E32-AA15-A6A200EFD57A,New & Notable,curated,14,MLB,0
...
5E976DF9-31F2-461F-BC78-17AB6B5132C8,Top Free Movies & TV,curated,151908,The Roku Channel,0
5E976DF9-31F2-461F-BC78-17AB6B5132C8,Top Free Movies & TV,curated,2595,Crunchyroll,0
5E976DF9-31F2-461F-BC78-17AB6B5132C8,Top Free Movies & TV,curated,13535,Plex - Stream for Free,0
5E976DF9-31F2-461F-BC78-17AB6B5132C8,Top Free Movies & TV,curated,74519,Pluto TV - It's Free TV,0
5E976DF9-31F2-461F-BC78-17AB6B5132C8,Top Free Movies & TV,curated,41468,Tubi - Free Movies & TV,0
...
58F8F920-F0DA-43B8-B39F-F05544BBDE5C,TV en Español,tag,580590,IENTC TV,0
58F8F920-F0DA-43B8-B39F-F05544BBDE5C,TV en Español,tag,574190,Televisión Satelital,$18.99
58F8F920-F0DA-43B8-B39F-F05544BBDE5C,TV en Español,tag,574823,Canales Premium en Vivo,$18.99
58F8F920-F0DA-43B8-B39F-F05544BBDE5C,TV en Español,tag,194548,Telemicro,0
58F8F920-F0DA-43B8-B39F-F05544BBDE5C,TV en Español,tag,596854,Talanga Vision,0
```

## Crawling the Roku Channel Store for channel details (metadata)
The [`roku_channel_details_crawler.py`](/scripts/roku_channel_details_crawler.py) script crawls the Roku Channel Store to obtain the complete channel details (metadata) for all channels in a user-specified set of Roku channels. The script expects two positional arguments, and also allows for an additional optional argument:
```
$ python3 roku_channel_details_crawler.py -h
usage: roku_channel_details_crawler.py [-h] [--csv CSV]
                                       channel_ids_file out_json_file

Crawls the Roku Channel Store for channel details for a set of channels.

positional arguments:
  channel_ids_file  A file that defines the set of channels to fetch channel
                    details for. The format should be one channel ID (integer)
                    per line. Lines starting with '#' are interpreted as
                    comments and are ignored.
  out_json_file     Output JSON file where channel details are to be written.

optional arguments:
  -h, --help        show this help message and exit
  --csv CSV         If a path to a .csv file is provided for this argument, a
                    subset of the full channel details (the JSON) will be
                    written to this csv file (currently only rating and
                    price).
```

The JSON output is formatted as a single root object with a key/value entry for each (valid) channel ID. The key is the channel ID, and its associated value is another JSON object which holds all metadata for that respective channel:
```
{
  "12": {
    ... object describing the metadata of app with id=12 ...
  },
  "13": {
    ... object describing the metadata of app with id=13 ...
  },
  "14": {
    ... object describing the metadata of app with id=14 ...
  }
  ...
}
```
The metadata is structured in exactly the same way as when it was originally returned from the API backing the Roku Channel Store. To the best of our knowledge, Roku has not made a description of this JSON structure publicly available, but most of the key names do a good job at describing their associated value.

The resulting JSON file can be very large, which may make it difficult to work with/process: for example, retrieving channel metadata for all available channels as of August 11th, 2020 (14,196 channels) resulted in a 122 MB JSON file. If you are only interested in ranking the channels according to their popularity and/or price, you can use the optional `--csv your_csv_file.csv` to produce a CSV file (which will be output alongside the main JSON file) with the following format:
```
channel_id,rating,star_rating,star_rating_count,price_as_number
12,76.2225,76.2225,3707863,0
13,74.3087,74.3087,765942,0
14,76.7029,76.7029,45032,0
...
```
You can then import this CSV file into spreadsheet software such as Microsoft Excel or an SQL database to enable easy sorting by the different rating/price metrics. The CSV file will be much smaller as it only includes a very small subset of all available metadata. For comparison, the CSV file corresponding to the 122 MB JSON file, mentioned earlier, is 367 KB.

# License
Rokustic is licensed under [Apache License, version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

# Potential future improvements
- Support for automatically uninstalling Roku apps.
- Migrate key press sequences to configuration/input files to make app interaction strategy more flexible.

# Acknowledgements
- [Pcap4J](https://github.com/kaitoy/pcap4j)
- [unirest-java](https://github.com/Mashape/unirest-java)
- [jackson-dataformat-xml](https://github.com/FasterXML/jackson-dataformat-xml)
- [ssdp-client](https://github.com/vmichalak/ssdp-client)
- [slf4j](http://www.slf4j.org/)
- [junit](https://github.com/junit-team/junit4)
