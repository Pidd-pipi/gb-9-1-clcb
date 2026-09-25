import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Typography,
  Button,
  List,
  Tag,
  Avatar,
  Descriptions,
  message,
  Spin,
  Modal,
  Upload,
  Form,
  Input,
  Space,
} from 'antd'
import {
  PlayCircleOutlined,
  ClockCircleOutlined,
  LockOutlined,
  CloudUploadOutlined,
} from '@ant-design/icons'
import type { UploadProps } from 'antd'
import { audioApi } from '../api/audio'
import type { AudioCourse } from '../types'
import { useSelector } from 'react-redux'
import { RootState } from '../store'

const { Title, Text, Paragraph } = Typography

function AudioCourseDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [course, setCourse] = useState<AudioCourse | null>(null)
  const [loading, setLoading] = useState(false)
  const [purchasing, setPurchasing] = useState(false)
  const [uploadVisible, setUploadVisible] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [form] = Form.useForm()
  const [fileList, setFileList] = useState<any[]>([])
  const { user } = useSelector((state: RootState) => state.auth)

  useEffect(() => {
    if (id) {
      loadCourseDetail()
    }
  }, [id])

  const loadCourseDetail = async () => {
    if (!id) return
    setLoading(true)
    try {
      const res = await audioApi.getById(id)
      const data = res.data?.data
      if (data) {
        setCourse(data)
      }
    } catch (error) {
      console.error('Failed to load course:', error)
    } finally {
      setLoading(false)
    }
  }

  const handlePurchase = async () => {
    if (!id) return
    if (!user) {
      message.warning('请先登录后再购买')
      navigate('/login')
      return
    }
    setPurchasing(true)
    try {
      await audioApi.purchase(id)
      message.success('购买成功')
      await loadCourseDetail()
    } catch (error) {
      console.error('Purchase failed:', error)
    } finally {
      setPurchasing(false)
    }
  }

  const handlePlay = (episodeId: string) => {
    navigate(`/audio/play/${id}/${episodeId}`)
  }

  const handleContinue = () => {
    if (!course) return
    const episodeId = course.continueEpisodeId || course.episodes?.[0]?.id
    if (episodeId) {
      handlePlay(episodeId)
    }
  }

  const handleOffline = async () => {
    if (!id) return
    Modal.confirm({
      title: '确认下架该课程？',
      content: '下架后新用户无法购买，已购用户仍可继续学习。',
      okText: '确认下架',
      cancelText: '取消',
      onOk: async () => {
        try {
          await audioApi.offline(id)
          message.success('课程已下架')
          await loadCourseDetail()
        } catch (error) {
          console.error('Offline failed:', error)
        }
      },
    })
  }

  const handlePublish = async () => {
    if (!id) return
    try {
      await audioApi.publish(id)
      message.success('课程已上架')
      await loadCourseDetail()
    } catch (error) {
      console.error('Publish failed:', error)
    }
  }

  const handleUpload = async () => {
    if (!id) return
    const values = await form.validateFields()
    const file = fileList[0]?.originFileObj as File | undefined
    if (!file) {
      message.warning('请选择音频文件')
      return
    }
    setUploading(true)
    setUploadProgress(0)
    try {
      await audioApi.uploadEpisode(
        id,
        { file, title: values.title, description: values.description },
        setUploadProgress
      )
      message.success('音频上传成功')
      setUploadVisible(false)
      form.resetFields()
      setFileList([])
      await loadCourseDetail()
    } catch (error) {
      console.error('Upload failed:', error)
    } finally {
      setUploading(false)
    }
  }

  const uploadProps: UploadProps = {
    beforeUpload: (file) => {
      if (!file.type.startsWith('audio/')) {
        message.error('只能上传音频文件')
        return Upload.LIST_IGNORE
      }
      setFileList([file])
      form.setFieldValue('title', form.getFieldValue('title') || file.name)
      return false
    },
    onRemove: () => setFileList([]),
    maxCount: 1,
    fileList,
  }

  const formatDuration = (seconds: number | undefined) => {
    if (!seconds) return '0:00'
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  if (loading || !course) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  const isOffline = course.status === 'OFFLINE'
  const isOwner = !!course.owner

  const renderActionArea = () => {
    if (isOwner) {
      return (
        <Space>
          <Button type="primary" size="large" onClick={handleContinue}>
            预览课程
          </Button>
          <Button size="large" icon={<CloudUploadOutlined />} onClick={() => setUploadVisible(true)}>
            上传单集
          </Button>
          {isOffline ? (
            <Button size="large" onClick={handlePublish}>
              重新上架
            </Button>
          ) : (
            <Button size="large" danger onClick={handleOffline}>
              下架课程
            </Button>
          )}
        </Space>
      )
    }
    if (course.purchased) {
      return (
        <Space>
          <Tag color="green" style={{ fontSize: 16, padding: '4px 12px' }}>
            ✓ 已购买
          </Tag>
          <Button type="primary" size="large" onClick={handleContinue}>
            {course.continueEpisodeId ? '继续学习' : '开始学习'}
          </Button>
        </Space>
      )
    }
    return (
      <Space>
        <Button size="large" onClick={() => handlePlay(course.episodes?.[0]?.id || '')}>
          免费试听
        </Button>
        <Button
          type="primary"
          size="large"
          onClick={handlePurchase}
          loading={purchasing}
          disabled={isOffline}
        >
          {isOffline ? '已下架' : `立即购买 ¥${course.price}`}
        </Button>
      </Space>
    )
  }

  return (
    <div>
      <Card>
        <div style={{ display: 'flex', gap: 24 }}>
          <div
            style={{
              width: 240,
              height: 240,
              background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              fontSize: 96,
              flexShrink: 0,
            }}
          >
            🎧
          </div>
          <div style={{ flex: 1 }}>
            <Title level={2}>
              {course.title}{' '}
              {isOffline && <Tag color="default">已下架</Tag>}
              {course.status === 'DRAFT' && <Tag color="orange">草稿</Tag>}
            </Title>
            <div style={{ marginBottom: 16 }}>
              <Avatar icon={<span>👤</span>} src={course.creator?.avatar} />
              <Text style={{ marginLeft: 8 }}>{course.creator?.username}</Text>
              {course.isSeries && (
                <Tag color="purple" style={{ marginLeft: 8 }}>
                  系列课
                </Tag>
              )}
            </div>
            <Paragraph type="secondary">{course.description}</Paragraph>
            <Descriptions column={3} style={{ marginTop: 16 }}>
              <Descriptions.Item label="集数">{course.episodeCount}</Descriptions.Item>
              <Descriptions.Item label="总时长">
                {formatDuration(course.totalDuration)}
              </Descriptions.Item>
              <Descriptions.Item label="价格" className="price-text">
                ¥{course.price}
              </Descriptions.Item>
            </Descriptions>
            {!course.purchased && !isOwner && (
              <Text type="secondary">游客可免费试听第一集前 {course.trialSeconds ?? 60} 秒</Text>
            )}
            <div style={{ marginTop: 24 }}>{renderActionArea()}</div>
          </div>
        </div>
      </Card>

      <Card title="课程目录" style={{ marginTop: 24 }}>
        <List
          dataSource={course.episodes || []}
          locale={{
            emptyText: `暂无单集${isOwner ? '，点击「上传单集」发布音频' : ''}`,
          }}
          renderItem={(episode, index) => {
            const unlocked = !!course.purchased || isOwner || index === 0
            return (
              <List.Item
                actions={[
                  unlocked ? (
                    <Button
                      type="link"
                      key="play"
                      icon={<PlayCircleOutlined />}
                      onClick={() => handlePlay(episode.id)}
                    >
                      {index === 0 && !course.purchased && !isOwner
                        ? '试听'
                        : episode.completed
                          ? '重听'
                          : episode.progressPosition
                            ? '继续播放'
                            : '播放'}
                    </Button>
                  ) : (
                    <Button type="link" key="locked" icon={<LockOutlined />} disabled>
                      购买后可听
                    </Button>
                  ),
                ]}
              >
                <List.Item.Meta
                  title={
                    <span>
                      <Text type="secondary" style={{ marginRight: 12 }}>
                        #{index + 1}
                      </Text>
                      {episode.title}
                      {index === 0 && !course.purchased && !isOwner && (
                        <Tag color="blue" style={{ marginLeft: 8 }}>
                          免费试听
                        </Tag>
                      )}
                    </span>
                  }
                  description={
                    <span>
                      <ClockCircleOutlined style={{ marginRight: 4 }} />
                      {formatDuration(episode.duration)}
                    </span>
                  }
                />
              </List.Item>
            )
          }}
        />
      </Card>

      <Modal
        title="上传单集"
        open={uploadVisible}
        onOk={handleUpload}
        onCancel={() => setUploadVisible(false)}
        confirmLoading={uploading}
        okText="上传"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="单集标题" rules={[{ required: true, message: '请输入单集标题' }]}>
            <Input placeholder="请输入单集标题" />
          </Form.Item>
          <Form.Item name="description" label="单集简介">
            <Input.TextArea rows={3} placeholder="请输入单集简介" />
          </Form.Item>
          <Form.Item label="音频文件" required>
            <Upload.Dragger {...uploadProps} accept="audio/*">
              <p className="ant-upload-drag-icon">
                <CloudUploadOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽音频文件到此处上传</p>
              {uploading && (
                <p className="ant-upload-hint">上传中 {uploadProgress}%</p>
              )}
            </Upload.Dragger>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default AudioCourseDetail
