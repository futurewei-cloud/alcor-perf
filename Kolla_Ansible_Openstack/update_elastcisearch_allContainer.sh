for container in `docker ps -q`; do
  docker exec -u 0 -it $container pip install -U elasticsearch;
done