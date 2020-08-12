import requests
import json
import argparse
import unicodecsv as csv

api_version = "v6"
base_url = "https://channelstore.roku.com/api/" + api_version + "/channels/detailsunion"


# ======================================================================================================================
# util functions for producing query param tuples (which can be provided to requests lib)

def qp_country(value="US"):
    return "country", value


def qp_language(value="en"):
    return "language", value

# ======================================================================================================================


def get_channel_details(chan_id):
    """
    Get channel details for a given channel.
    :param chan_id: The ID of the channel to fetch details for.
    :return: The channel details in JSON format.
    """
    url = base_url + "/" + str(chan_id)
    query_params = [qp_country(), qp_language()]
    resp = requests.get(url, params=query_params)
    if resp.status_code != requests.codes.ok:
        print("WARNING: failed getting details for channel with id=" + str(chan_id))
        return None
    return resp.json()


def write_json(data, file_out):
    """
    Write JSON to a file.
    :param data: In-memory JSON.
    :param file_out: The file to output the JSON to.
    """
    with open(file_out, "w") as jf:
        jf.seek(0)
        jf.write(json.dumps(data, sort_keys=False, indent=2))
        jf.truncate()


def write_csv(json_result, csv_filepath):
    """
    Write a subset of the json resulting from the details crawl in csv format (currently only rating and price).
    :param json_result: The json resulting from the details crawl (for a set of channels).
    :param csv_filepath: The path to the csv file.
    :return: None
    """
    header_row = ["channel_id", "rating", "star_rating", "star_rating_count", "price_as_number"]
    with open(csv_filepath, "wb") as csv_file:
        csv_writer = csv.writer(csv_file, encoding='utf-8')
        csv_writer.writerow(header_row)
        for chan_id in json_result:
            details = json_result[chan_id]["details"]
            rating = details["rating"]
            star_rating = details["starRating"]
            star_rating_cnt = details["starRatingCount"]
            price_as_number = details["priceAsNumber"]
            csv_writer.writerow([chan_id, rating, star_rating, star_rating_cnt, price_as_number])


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description="Crawls the Roku Channel Store for channel details for a set of channels.")
    ap.add_argument("channel_ids_file", help="A file that defines the set of channels to fetch channel details for. " +
                    "The format should be one channel ID (integer) per line. Lines starting with '#' are interpreted " +
                    "as comments and are ignored.")
    ap.add_argument("out_json_file", help="Output JSON file where channel details are to be written.")
    ap.add_argument("--csv", help="If a path to a .csv file is provided for this argument, a subset of the full " +
                    "channel details (the JSON) will be written to this csv file (currently only rating and price).")
    args = ap.parse_args()
    json_result = {}
    with open(args.channel_ids_file, "r") as in_file:
        # Remove duplicate channel ids in input.
        chan_ids = set()
        for line in in_file.readlines():
            if line.startswith("#"):
                continue
            chan_ids.add(int(line))
        # Crawl channel details for all unique channel ids.
        for chan_id in sorted(chan_ids):
            progress = round((len(json_result) / len(chan_ids)) * 100)
            print(f'[{progress:3d}%] Fetching channel details for channel id={str(chan_id)}.')
            details_json = get_channel_details(chan_id)
            if details_json is None:
                continue
            json_result[chan_id] = details_json
    print("Writing .json file...")
    write_json(json_result, args.out_json_file)
    if args.csv is not None:
        print("Writing .csv file...")
        write_csv(json_result, args.csv)
