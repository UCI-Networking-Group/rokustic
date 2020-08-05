import requests
import json
import argparse
import unicodecsv as csv

api_version = "v6"
base_url = "https://channelstore.roku.com/api/" + api_version + "/channels"
categories_url = base_url + "/categories"

key_id = "id"
key_name = "name"
key_category_type = "categoryType"
key_price = "price"

default_page_size = 24


# ======================================================================================================================
# util functions for producing query param tuples (which can be provided to requests lib)
def qp_country(value="US"):
    return "country", value


def qp_language(value="en"):
    return "language", value


def qp_category(value):
    return "category", value


def qp_pagestart(page=0):
    return "pagestart", str(page)


def qp_pagesize(entries_per_page):
    return "pagesize", str(entries_per_page)


def qp_categorytype(type):
    return "categoryType", type

# ======================================================================================================================


def get_categories():
    """
    Crawl the Roku Channel Store for the set of channel categories it specifies.
    :return: The set of channel categories specified by the Roku Channel Store, in JSON format.
    """
    # URL example: https://channelstore.roku.com/api/v6/channels/categories?country=US&language=en
    query_params = [qp_country(), qp_language()]
    resp = requests.get(categories_url, params=query_params)
    if resp.status_code != requests.codes.ok:
        print("WARNING: categories query returned non-200 response")
        return None
    return resp.json()


def crawl_category(category_id, category_type, channel_filter=None, page_size=default_page_size):
    """
    Crawl the Roku Channel Store for channels pertaining to a given category.
    :param category_id: The ID of the category.
    :param category_type: The category's type. Known possible values: 'curated' and 'tag' ('algo' possibly deprecated).
    :param channel_filter: A filter that specifies if a given channel should be included in the resulting list. All
    channels are included by default. A filter implementation should return True if the given channel should be included
    and False otherwise. The JSON representation of the channel is provided as an argument to this filter function.
    :param page_size: Number of channels the server should return for each requests. This function will accumulate the
    channels returned from multiple requests to the server, so the output should be the same irrespective of the value
    of this parameter.
    :return: A list of channels in that category.
    """
    # URL example: https://channelstore.roku.com/api/v6
    # /channels?country=US&language=en&category=TopFree&pagestart=1&pagesize=24&categoryType=algo
    channels = []
    page = 0
    while True:
        query_params = [qp_country(), qp_language(), qp_category(category_id), qp_pagestart(page),
                        qp_pagesize(page_size), qp_categorytype(category_type)]
        resp = requests.get(base_url, params=query_params)
        if resp.status_code != requests.codes.ok:
            print("WARNING: encountered non-200 response while crawling category '" + category_id + "'")
            break
        resp_json = resp.json()
        channel_count = 0
        for chan in resp_json:
            channel_count += 1
            if channel_filter is None or channel_filter(chan):
                # Include all channels if no filter was specified.
                # Or only include channels that pass the filter, if one was specified.
                channels.append(chan)
        if channel_count < page_size:
            # ==========================================================================================================
            # TODO:
            # It seems that the server keeps returning the same set of channels irrespective of the page number when the
            # number of channels in the category is less than the page size. Hence, we can break the loop by checking
            # for this condition here. However, it is not clear what happens when the number of channels is exactly
            # equal to the page size. If the server ignores the page number and keeps returning the same set of channels
            # we need to check if the previous response body was identical to the current response body to break the
            # loop.
            # ==========================================================================================================
            # Done fetching all channels in this category.
            break
        page += 1
    return channels


def free_channels_only_filter(channel):
    """
    Filter that can be provided as argument to crawl_category. Ensures that only free apps are included in the output.
    :param channel: The channel to check the filter against.
    :return: True if the channel should be included in the output, False otherwise.
    """
    return channel[key_price] == "0"


def write_json(data, file_out):
    """
    Write JSON to a file.
    :param data: In-memory JSON.
    :param file_out: The file to output the JSON to.
    """
    with open(file_out, "w") as jf:
        jf.seek(0)
        jf.write(json.dumps(data, sort_keys=False, indent=4))
        jf.truncate()


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description="Crawls the Roku Channel Store to obtain a list of available channels.")
    ap.add_argument('out_file', help='File where the output of the crawl is to be written. Output is in csv format.')
    args = ap.parse_args()

    header_row = ["category_id", "category_name", "category_type", "chanenl_id", "channel_name", "channel_price"]

    with open(args.out_file, "wb") as csv_file:
        csv_writer = csv.writer(csv_file, encoding='utf-8')
        csv_writer.writerow(header_row)
        categories = get_categories()
        for cat in categories:
            print("Crawling category '" + cat[key_name] + "'")

            # Retain only free channels
            # cat_channels = crawl_category(cat[key_id], cat[key_category_type], free_channels_only_filter)

            # Retain all channels, irrespective of price
            cat_channels = crawl_category(cat[key_id], cat[key_category_type])

            print("Found " + str(len(cat_channels)) + " channels in category.")

            # Channels are returned as a JSON list.
            # For each channel, extract the relevant information and add a csv row for that channel.
            for channel in cat_channels:
                csv_writer.writerow([cat[key_id], cat[key_name], cat[key_category_type], channel[key_id],
                                    channel[key_name], channel[key_price]])
