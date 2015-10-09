# Bulk Data Storage System Tools

Command line utility for working with Bulk Data Storage System content, available at https://data.uspto.gov.

## Prerequisites

Java 7 or above. The ability to connect to the internet (for downloading Gradle when running the wrapper script)

## Building

    gradlew assemble

## Running

Using gradle itself: 

    gradlew run -Dexec.args="{args}"

Using the zip or tar distribution, located under `build\distributions`, use the script in the extracted `/bin` directory:

    ./bulk-data-tools {args}

## Tools

At this time, the only tool available is an XML spitter.

### XML Splitter

The splitter takes as input an "XML" file that is actually a series of concatenated XML files, and splits them into individual XML files. Some of USPTO Bulk products, such as [Patent Grant Full Text Data/XML Version 4.5 ICE](https://data.uspto.gov/data2/patent/grant/redbook/fulltext/2015/), 
come in this format (zipped). 

### Usage

When no `{args}` are provided, the splitter will read data from stdin and output the XML segments to a directory,
`splitxml`, relative to the location the application is run. To specify input other than stdin, provide an `--in` arg.
To specify a different output directory, provide an `--out` arg. Non-existent output directories will be created as needed.

*Example:* read from local XML file and output to default directory.

    gradlew run -Dexec.args="--in /path/to/ipg150106.xml"
    
    ./bulk-data-tools --in /path/to/ipg150106.xml

*Example:* read from local ZIP file and output to specific local directory

    gradlew run -Dexec.args="--in /path/to/ipg150106.zip --out /path/to/directory"
    
    ./bulk-data-tools --in /path/to/ipg150106.zip --out /path/to/directory

*Example:* show help usage

    gradlew run -Dexec.args="--help"
    
    ./bulk-data-tools --help

## TODO

Support URLs in --in flag to download directly from https://data.uspto.gov/
