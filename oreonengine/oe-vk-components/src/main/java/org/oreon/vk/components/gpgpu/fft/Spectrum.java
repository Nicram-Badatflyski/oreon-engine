package org.oreon.vk.components.gpgpu.fft;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_STORAGE_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;

import java.nio.ByteBuffer;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.oreon.core.math.Vec2f;
import org.oreon.core.vk.core.command.CommandBuffer;
import org.oreon.core.vk.core.command.SubmitInfo;
import org.oreon.core.vk.core.context.VkContext;
import org.oreon.core.vk.core.descriptor.Descriptor;
import org.oreon.core.vk.core.descriptor.DescriptorSet;
import org.oreon.core.vk.core.descriptor.DescriptorSetLayout;
import org.oreon.core.vk.core.image.VkImage;
import org.oreon.core.vk.core.image.VkImageView;
import org.oreon.core.vk.core.pipeline.VkPipeline;
import org.oreon.core.vk.core.synchronization.Fence;
import org.oreon.core.vk.core.util.VkUtil;
import org.oreon.core.vk.wrapper.command.ComputeCmdBuffer;
import org.oreon.core.vk.wrapper.image.Image2DLocal;
import org.oreon.core.vk.wrapper.image.VkImageHelper;
import org.oreon.core.vk.wrapper.shader.ComputeShader;

import lombok.Getter;

public class Spectrum {

	@Getter
	private VkImageView h0k_imageView;
	@Getter
	private VkImageView h0minusk_imageView;
	
	private VkImage h0k_image;
	private VkImage h0minusk_image;
	
	private class SpectrumDescriptor extends Descriptor{
		
		public SpectrumDescriptor(VkDevice device,
				VkImageView noise0, VkImageView noise1,
				VkImageView noise2, VkImageView noise3){
			
			layout = new DescriptorSetLayout(device, 6);
		    layout.addLayoutBinding(0, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
		    		VK_SHADER_STAGE_COMPUTE_BIT);
		    layout.addLayoutBinding(1, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
		    		VK_SHADER_STAGE_COMPUTE_BIT);
		    layout.addLayoutBinding(2, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
		    		VK_SHADER_STAGE_COMPUTE_BIT);
		    layout.addLayoutBinding(3, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
		    		VK_SHADER_STAGE_COMPUTE_BIT);
		    layout.addLayoutBinding(4, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
		    		VK_SHADER_STAGE_COMPUTE_BIT);
		    layout.addLayoutBinding(5, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
		    		VK_SHADER_STAGE_COMPUTE_BIT);
		    layout.create();
		    
		    set = new DescriptorSet(device,
		    		VkContext.getDescriptorPoolManager().getDescriptorPool("POOL_1").getHandle(),
		    		layout.getHandlePointer());
		    set.updateDescriptorImageBuffer(h0k_imageView.getHandle(),
		    		VK_IMAGE_LAYOUT_GENERAL, -1,
		    		0, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
		    set.updateDescriptorImageBuffer(h0minusk_imageView.getHandle(),
		    		VK_IMAGE_LAYOUT_GENERAL, -1,
		    		1, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
		    set.updateDescriptorImageBuffer(noise0.getHandle(), VK_IMAGE_LAYOUT_GENERAL,
		    		-1, 2, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
		    set.updateDescriptorImageBuffer(noise1.getHandle(), VK_IMAGE_LAYOUT_GENERAL,
		    		-1, 3, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
		    set.updateDescriptorImageBuffer(noise2.getHandle(), VK_IMAGE_LAYOUT_GENERAL,
		    		-1, 4, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
		    set.updateDescriptorImageBuffer(noise3.getHandle(), VK_IMAGE_LAYOUT_GENERAL,
		    		-1, 5, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
		}
	}
	
	public Spectrum(VkDevice device,
			VkPhysicalDeviceMemoryProperties memoryProperties,
			int N, int L, float amplitude, Vec2f windDirection, float windSpeed,
			float capillarSuppressFactor) {
		
		h0k_image = new Image2DLocal(device, memoryProperties, N, N,
				VK_FORMAT_R32G32B32A32_SFLOAT, VK_IMAGE_USAGE_STORAGE_BIT);
		
		h0k_imageView = new VkImageView(device,
				VK_FORMAT_R32G32B32A32_SFLOAT, h0k_image.getHandle(), VK_IMAGE_ASPECT_COLOR_BIT);
		
		h0minusk_image = new Image2DLocal(device, memoryProperties, N, N,
				VK_FORMAT_R32G32B32A32_SFLOAT, VK_IMAGE_USAGE_STORAGE_BIT);
		
		h0minusk_imageView = new VkImageView(device,
				VK_FORMAT_R32G32B32A32_SFLOAT, h0minusk_image.getHandle(), VK_IMAGE_ASPECT_COLOR_BIT);
		
		VkImage noise0Image = VkImageHelper.createStorageImageFromFile(
				device,
				VkContext.getPhysicalDevice().getMemoryProperties(),
				VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
				VkContext.getLogicalDevice().getTransferQueue(),
				"textures/noise/Noise" + N + "_0.jpg");
		
		VkImageView noise0ImageView = new VkImageView(device,
				VK_FORMAT_R8G8B8A8_UNORM, noise0Image.getHandle(), VK_IMAGE_ASPECT_COLOR_BIT);
		
		VkImage noise1Image = VkImageHelper.createStorageImageFromFile(
				device,
				VkContext.getPhysicalDevice().getMemoryProperties(),
				VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
				VkContext.getLogicalDevice().getTransferQueue(),
				"textures/noise/Noise" + N + "_1.jpg");
		
		VkImageView noise1ImageView = new VkImageView(device,
				VK_FORMAT_R8G8B8A8_UNORM, noise1Image.getHandle(), VK_IMAGE_ASPECT_COLOR_BIT);
		
		VkImage noise2Image = VkImageHelper.createStorageImageFromFile(
				device,
				VkContext.getPhysicalDevice().getMemoryProperties(),
				VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
				VkContext.getLogicalDevice().getTransferQueue(),
				"textures/noise/Noise" + N + "_2.jpg");
		
		VkImageView noise2ImageView = new VkImageView(device,
				VK_FORMAT_R8G8B8A8_UNORM, noise2Image.getHandle(), VK_IMAGE_ASPECT_COLOR_BIT);
		
		VkImage noise3Image = VkImageHelper.createStorageImageFromFile(
				device,
				VkContext.getPhysicalDevice().getMemoryProperties(),
				VkContext.getLogicalDevice().getTransferCommandPool().getHandle(),
				VkContext.getLogicalDevice().getTransferQueue(),
				"textures/noise/Noise" + N + "_3.jpg");
		
		VkImageView noise3ImageView = new VkImageView(device,
				VK_FORMAT_R8G8B8A8_UNORM, noise3Image.getHandle(), VK_IMAGE_ASPECT_COLOR_BIT);
		
		int pushConstantRange = Integer.BYTES * 2 + Float.BYTES * 6;
		
		ByteBuffer pushConstants = memAlloc(pushConstantRange);
		pushConstants.putInt(N);
		pushConstants.putInt(L);
		pushConstants.putFloat(amplitude);
		pushConstants.putFloat(windSpeed);
		pushConstants.putFloat(windDirection.getX());
		pushConstants.putFloat(windDirection.getY());
		pushConstants.putFloat(capillarSuppressFactor);
		pushConstants.putFloat(0);
		pushConstants.flip();
		
		Descriptor descriptor = new SpectrumDescriptor(device,
				noise0ImageView, noise1ImageView, noise2ImageView, noise3ImageView);
		
		VkPipeline pipeline = new VkPipeline(device);
		pipeline.setPushConstantsRange(VK_SHADER_STAGE_COMPUTE_BIT, pushConstantRange);
		pipeline.setLayout(descriptor.getLayout().getHandlePointer());
		pipeline.createComputePipeline(new ComputeShader(device, "fft/~h0.comp.spv"));
		
		CommandBuffer commandBuffer = new ComputeCmdBuffer(device,
				VkContext.getLogicalDevice().getComputeCommandPool().getHandle(),
				pipeline.getHandle(), pipeline.getLayoutHandle(),
				VkUtil.createLongArray(descriptor.getSet()), N/16, N/16, 1,
				pushConstants, VK_SHADER_STAGE_COMPUTE_BIT);
		
		Fence fence = new Fence(device);
		
		SubmitInfo submitInfo = new SubmitInfo();
		submitInfo.setCommandBuffers(commandBuffer.getHandlePointer());
		submitInfo.setFence(fence);
		submitInfo.submit(VkContext.getLogicalDevice().getComputeQueue());
		
		fence.waitForFence();
		
		pipeline.destroy();
		commandBuffer.destroy();
		fence.destroy();
		descriptor.destroy();
		memFree(pushConstants);
		
		noise0Image.destroy();
		noise1Image.destroy();
		noise2Image.destroy();
		noise3Image.destroy();
		noise0ImageView.destroy();
		noise1ImageView.destroy();
		noise2ImageView.destroy();
		noise3ImageView.destroy();
	}
	
	public void destroy(){
		
		h0k_image.destroy();
		h0k_imageView.destroy();
		h0minusk_image.destroy();
		h0minusk_imageView.destroy();
	}
}
