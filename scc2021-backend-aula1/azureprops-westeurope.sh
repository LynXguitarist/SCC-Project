az functionapp config appsettings set --name sccfunwesteuropeafr --resource-group scc2021-rg-westeurope-afr --settings "BlobStoreConnection=DefaultEndpointsProtocol=https;AccountName=sccstorewesteuropeafr;AccountKey=5oZpL5X+5jBC9tjewoiTZhSa1XMq95PWaH/gLsF5duX/j2avGwTdpklj3uSNIYA0GJAlyo/dp3sJGHwx5HBdHA==;EndpointSuffix=core.windows.net"
az functionapp config appsettings set --name sccappwesteuropeafr --resource-group scc2021-rg-westeurope-afr --settings "BlobStoreConnection=DefaultEndpointsProtocol=https;AccountName=sccstorewesteuropeafr;AccountKey=5oZpL5X+5jBC9tjewoiTZhSa1XMq95PWaH/gLsF5duX/j2avGwTdpklj3uSNIYA0GJAlyo/dp3sJGHwx5HBdHA==;EndpointSuffix=core.windows.net"
az functionapp config appsettings set --name sccappwesteuropeafr --resource-group scc2021-rg-westeurope-afr --settings "COSMOSDB_KEY=inKOxAByrFB0JxkWJSiKZ8gJKfEXEIrFATVcyfePwg0l1QhFFUgX5LgHx6EUwJR9gFd6PuhVJZLWJ2UfgZPHEw=="
az functionapp config appsettings set --name sccappwesteuropeafr --resource-group scc2021-rg-westeurope-afr --settings "COSMOSDB_URL=https://scc-groupbdafr.documents.azure.com:443/"
az functionapp config appsettings set --name sccappwesteuropeafr --resource-group scc2021-rg-westeurope-afr --settings "COSMOSDB_DATABASE=sccBDafr"
az functionapp config appsettings set --name sccfunwesteuropeafr --resource-group scc2021-rg-westeurope-afr --settings "AzureCosmosDBConnection=AccountEndpoint=https://scc-groupbdafr.documents.azure.com:443/;AccountKey=inKOxAByrFB0JxkWJSiKZ8gJKfEXEIrFATVcyfePwg0l1QhFFUgX5LgHx6EUwJR9gFd6PuhVJZLWJ2UfgZPHEw==;"