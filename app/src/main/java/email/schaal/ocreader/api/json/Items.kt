package email.schaal.ocreader.api.json

import email.schaal.ocreader.database.model.Item

/**
 * Class to deserialize the json response for items
 */
class Items(val items: List<Item>)