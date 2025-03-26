docker run -d -p 27017:27017 --name mongo \
    -e MONGO_INITDB_ROOT_USERNAME=root \
    -e MONGO_INITDB_ROOT_PASSWORD=root \
    -v ~/Desktop/db/mongo:/data/db \
    mongo