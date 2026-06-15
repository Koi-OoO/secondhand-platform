<template>
  <div class="publish-page">
    <div class="publish-card">
      <div class="publish-header">
        <h2>{{ isEdit ? '编辑商品' : '发布商品' }}</h2>
        <p>{{ isEdit ? '调整库存、价格和图片后保存' : '填写商品信息并设置可售库存' }}</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="商品图片" prop="imageUrls">
          <el-upload
            v-model:file-list="fileList"
            list-type="picture-card"
            :http-request="handleUpload"
            :limit="9"
            :on-remove="handleRemove"
            :before-upload="beforeUpload"
            accept="image/jpeg,image/png,image/webp"
          >
            <el-icon><Camera /></el-icon>
          </el-upload>
          <div class="form-tip">最多 9 张，第一张会作为首图。</div>
        </el-form-item>

        <el-form-item label="商品标题" prop="title">
          <el-input
            v-model="form.title"
            maxlength="100"
            show-word-limit
            placeholder="例如：iPhone 13 128G 国行 蓝色"
          />
        </el-form-item>

        <el-form-item label="商品分类" prop="categoryId">
          <el-cascader
            v-model="selectedCategory"
            :options="categoryOptions"
            :props="{ value: 'id', label: 'name', children: 'children', emitPath: false }"
            class="full-width"
            placeholder="选择分类"
            @change="form.categoryId = $event"
          />
        </el-form-item>

        <el-form-item label="商品成色" prop="conditionLevel">
          <el-segmented
            v-model="form.conditionLevel"
            :options="conditionOptions"
            class="full-width"
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :xs="24" :md="8">
            <el-form-item label="售价" prop="price">
              <el-input-number v-model="form.price" :min="0.01" :precision="2" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="form.stock" :min="1" :precision="0" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="原价">
              <el-input-number v-model="form.originalPrice" :min="0" :precision="2" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="商品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="补充描述使用情况、配件、瑕疵和交易说明"
          />
        </el-form-item>

        <div class="actions">
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存修改' : '立即发布' }}
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { getCategories } from '@/api/category'
import { getProductDetail, publishProduct, updateProduct } from '@/api/product'
import { uploadImage } from '@/api/upload'
import { CONDITION } from '@/utils/constant'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const fileList = ref([])
const categoryOptions = ref([])
const selectedCategory = ref(null)
const submitting = ref(false)

const isEdit = computed(() => route.name === 'ProductEdit')
const editId = computed(() => (isEdit.value ? Number(route.params.id) : null))

const conditionOptions = Object.entries(CONDITION).map(([value, label]) => ({
  label,
  value: Number(value)
}))

const form = reactive({
  title: '',
  categoryId: null,
  conditionLevel: 2,
  price: null,
  stock: 1,
  originalPrice: null,
  description: '',
  imageUrls: []
})

const rules = {
  imageUrls: [{ type: 'array', min: 1, message: '请至少上传一张图片', trigger: 'change' }],
  title: [
    { required: true, message: '请输入商品标题', trigger: 'blur' },
    { min: 2, max: 100, message: '标题长度需在 2 到 100 个字符之间', trigger: 'blur' }
  ],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  conditionLevel: [{ required: true, message: '请选择商品成色', trigger: 'change' }],
  price: [{ required: true, message: '请输入售价', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
}

onMounted(async () => {
  await loadCategories()
  if (isEdit.value) {
    await loadProduct()
  }
})

async function loadCategories() {
  try {
    categoryOptions.value = await getCategories()
  } catch {
    categoryOptions.value = []
  }
}

async function loadProduct() {
  try {
    const data = await getProductDetail(editId.value)
    if (data.status === 3) {
      ElMessage.warning('已售罄商品不能编辑')
      router.replace('/user/products')
      return
    }
    if (data.status === 1) {
      ElMessage.warning('请先下架商品再编辑')
      router.replace('/user/products')
      return
    }
    form.title = data.title
    form.categoryId = data.categoryId
    form.conditionLevel = data.conditionLevel
    form.price = data.price
    form.stock = data.stock || 1
    form.originalPrice = data.originalPrice
    form.description = data.description || ''
    form.imageUrls = (data.images || []).map(item => item.url)
    fileList.value = form.imageUrls.map((url, index) => ({
      uid: index,
      name: `image-${index}`,
      url,
      status: 'success'
    }))
    selectedCategory.value = data.categoryId
  } catch {
    ElMessage.error('加载商品信息失败')
    router.replace('/user/products')
  }
}

function beforeUpload(file) {
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.error('仅支持 jpg、png、webp 格式')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

async function handleUpload({ file, onSuccess, onError }) {
  try {
    const url = await uploadImage(file)
    form.imageUrls.push(url)
    onSuccess({ url })
  } catch {
    onError()
  }
}

function handleRemove(file) {
  const targets = [file.url, file.response?.url].filter(Boolean)
  for (const url of targets) {
    const index = form.imageUrls.indexOf(url)
    if (index > -1) {
      form.imageUrls.splice(index, 1)
    }
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload = {
    title: form.title,
    categoryId: form.categoryId,
    conditionLevel: form.conditionLevel,
    price: form.price,
    stock: form.stock,
    originalPrice: form.originalPrice || null,
    description: form.description || null,
    imageUrls: form.imageUrls
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateProduct({ id: editId.value, ...payload })
      ElMessage.success('保存成功')
    } else {
      await publishProduct(payload)
      ElMessage.success('发布成功')
    }
    router.replace('/user/products')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables.scss' as *;

.publish-page {
  max-width: 820px;
  margin: 0 auto;
  padding: $space-lg 0 $space-2xl;
}

.publish-card {
  background: $color-bg-card;
  border: 1px solid $color-border-light;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
  padding: 28px;
}

.publish-header {
  margin-bottom: 24px;

  h2 {
    margin: 0 0 6px;
    font-size: $font-size-2xl;
    font-weight: $font-weight-bold;
    color: $color-text;
  }

  p {
    margin: 0;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
}

.form-tip {
  margin-top: 8px;
  font-size: $font-size-xs;
  color: $color-text-muted;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.full-width {
  width: 100%;
}
</style>
