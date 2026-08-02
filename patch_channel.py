import re

with open("src/main/java/kh/edu/istad/ite/features/channel/service/SalesChannelServiceImpl.java", "r") as f:
    content = f.read()

# Replace imports
content = content.replace("import kh.edu.istad.ite.features.catalog.dto.ItemResponse;", 
    "import kh.edu.istad.ite.features.catalog.dto.ItemResponse;\n"
    "import kh.edu.istad.ite.features.channel.dto.SalesChannelItemResponse;\n"
    "import kh.edu.istad.ite.features.channel.repository.ItemChannelRepository;\n"
    "import kh.edu.istad.ite.features.catalog.mapper.ItemMapper;")

content = content.replace("private final ItemRepository itemRepository;",
    "private final ItemRepository itemRepository;\n    private final ItemChannelRepository itemChannelRepository;\n    private final ItemMapper itemMapper;")

# Replace findItemsByChannel
find_items_new = """
    @Override
    public List<SalesChannelItemResponse> findItemsByChannel(String channelCode) {
        return itemChannelRepository.findBySalesChannelCodeAndIsEnabledTrue(channelCode)
                .stream()
                .map(ic -> SalesChannelItemResponse.builder()
                        .itemChannelId(ic.getId())
                        .item(itemMapper.toItemResponse(ic.getItem()))
                        .build())
                .toList();
    }
"""

content = re.sub(r'@Override\n\s*public List<ItemResponse> findItemsByChannel\(String channelCode\)\s*\{[^\}]+\}', find_items_new.strip(), content)

with open("src/main/java/kh/edu/istad/ite/features/channel/service/SalesChannelServiceImpl.java", "w") as f:
    f.write(content)

with open("src/main/java/kh/edu/istad/ite/features/channel/controller/SalesChannelController.java", "r") as f:
    c2 = f.read()
    
c2 = c2.replace("import kh.edu.istad.ite.features.catalog.dto.ItemResponse;", "import kh.edu.istad.ite.features.catalog.dto.ItemResponse;\nimport kh.edu.istad.ite.features.channel.dto.SalesChannelItemResponse;")
c2 = c2.replace("public List<ItemResponse> findItemsByChannel", "public List<SalesChannelItemResponse> findItemsByChannel")

with open("src/main/java/kh/edu/istad/ite/features/channel/controller/SalesChannelController.java", "w") as f:
    f.write(c2)
