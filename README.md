# Rokustic release preparation
This repository is for preparing Rokustic for release. **It should be considered deprecated/stale once Rokustic has been released on our public GitHub.**

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

# Hardware setup
Rokustic is designed to run on a (UNIX-based) machine that acts as a wireless access point (AP) and a gateway for devices connected to this AP. To eliminate the need for filtering the collected network traces (by IP of the Roku device), we recommend that you do *not* connect any other devices than the Roku itself to this AP. To start/stop Rokustic, set up the machine to enable SSH login on its wired interface. When you start Rokustic and tell it to log traffic on the wireless interface (to which the Roku is connected), any management (SSH) traffic on the wired interface will not become part of the collected network traces. We use a Raspberry Pi 3 Model B as the AP/gateway (depicted in the figure below), but any (UNIX-based) machine with a wireless and a wired interface should suffice.

![A diagram depicting the hardware setup we use for Rokustic. A Raspberry Pi 3 Model B is set up as a wireless access point and gateway. The Roku is connected to this wireless access point.](https://github.com/UCI-Networking-Group/rokustic/blob/master/images/rokustic-hardware-setup.png "Rokustic hardware setup")

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
If you select this option, you will be prompted for the name of the network interface that is to be the target of the packet capture. This will typically be `wlan0` if you run this program on a Raspberry Pi that acts as an access point that the Roku connects to.

After specifying the network interface, you will also be prompted for an output directory. The network traces captured during interaction with each app will be written to this output directory. There will be one network trace per app. The naming convention used for the network trace files is `app-<ID>.pcap` where `<ID>` is the ID of the app that was being automatically interacted with while the network trace was captured.

# Dependencies
Rokustic uses [Pcap4J](https://github.com/kaitoy/pcap4j) to capture network traffic and thus inherits [the platform requirements of Pcap4J](https://github.com/kaitoy/pcap4j#how-to-use) (in particular, the availability of a pcap native library). Gradle (Maven) will handle inclusion of the Pcap4J library itself (and additional libraries used by Rokustic) automatically when you build/run Rokustic using the provided Gradle Wrapper.

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
