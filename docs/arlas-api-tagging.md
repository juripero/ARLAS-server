# ARLAS Tagging API

This API is available for tagging `hits` in ARLAS `collections`.  
By tagging, we mean here adding a value to a field that is of type `Array`.
The value is added to the field of all the hits of a collection matching the `Search` part of the TagRequest.
If no `Search` is provided, then the whole collection is tagged.


The collection name, the path to the field carrying the tag and the tag values must be provided in order to tag a collection. 
If needed a `Search` can be provided to specify the hits to be tagged:

```shell
curl -X POST  \
    --header 'Accept: application/json;charset=utf-8' \
    -d '{ "search": {}, "tag": { "path": "plant.color","value": "pink"}}' \
    'http://...:9999/arlas/write/geodata/_tag?pretty=false'
```


In order to remove a tag, meaning a value from the field, the same tag request is sent, but on the `untag` endpoint:
```shell
curl -X POST  \
    --header 'Accept: application/json;charset=utf-8' \
    -d '{ "search": {}, "tag": { "path": "plant.color","value": "pink"}}' \
    'http://...:9999/arlas/write/geodata/_untag?pretty=false'
```


To remove all the tags for a given field, simply omit the value of the tag:
```shell
curl -X POST  \
    --header 'Accept: application/json;charset=utf-8' \
    -d '{ "search": {}, "tag": { "path": "plant.color"}}' \
    'http://...:9999/arlas/write/geodata/_untag?pretty=false'
```

!!! warning
    Only taggable fields can be tagged. In order to be taggable, a field must have its path provided in the `CollectionReference`, more specifically in `params.taggable_fields`.
