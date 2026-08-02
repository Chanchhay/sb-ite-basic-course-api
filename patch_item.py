import re

with open("src/main/java/kh/edu/istad/ite/features/catalog/service/ItemServiceImpl.java", "r") as f:
    content = f.read()

imports = """
import kh.edu.istad.ite.config.filter.RequestDto;
import kh.edu.istad.ite.config.specification.FilterSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
"""
content = content.replace("import kh.edu.istad.ite.shared.helper.BusinessHelper;", "import kh.edu.istad.ite.shared.helper.BusinessHelper;\n" + imports)

# We need to inject FilterSpecification<Item> filterSpecification
# In ItemServiceImpl, we might not know exactly the constructor parameters, but it's likely @RequiredArgsConstructor.
# So we can just add the field. Let's find "private final MinioUtil minioUtil;" or similar.
# Wait, if we just insert it before the first method, it will work.
# Let's search for "public ItemResponse createItem" and insert before it.

dep_str = "    private final FilterSpecification<Item> filterSpecification;\n"

content = content.replace("    public ItemResponse createItem", dep_str + "\n    public ItemResponse createItem")

# Add filterItems method
method_str = """
    @Override
    @Transactional(readOnly = true)
    public Page<ItemResponse> filterItems(UUID businessId, RequestDto requestDto, Pageable pageable) {
        businessHelper.findAccessibleBusiness(businessId);
        
        org.springframework.data.jpa.domain.Specification<Item> spec = filterSpecification.getSearchSpecificationDynamic(
                requestDto.getSearchRequestDto(), requestDto.getGlobalOperator());
                
        org.springframework.data.jpa.domain.Specification<Item> businessSpec = (root, query, cb) -> 
                cb.equal(root.get("business").get("id"), businessId);
                
        return itemRepository.findAll(businessSpec.and(spec), pageable).map(itemMapper::toItemResponse);
    }
"""
content = content.rstrip()
if content.endswith("}"):
    content = content[:-1] + method_str + "\n}\n"

with open("src/main/java/kh/edu/istad/ite/features/catalog/service/ItemServiceImpl.java", "w") as f:
    f.write(content)
