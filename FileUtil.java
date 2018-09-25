package com.collect.monitor.controller.model;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;





import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang.StringUtils;

import com.collect.monitor.controller.model.FileSetModel;
import com.pv.service.entity.PVAttachmentEntity;
import com.pv.service.service.PVAttachmentService;
import com.pv.service.tools.DateUtils;
import com.pv.service.tools.IDGenerator;

public class FileUtil {
	
	/**
	 * @描述 文件上传
	 * @创建时间 2017年8月9日11:13:11
	 * 
	 * */
	public List<PVAttachmentEntity> fileUpload(HttpServletRequest request, PVAttachmentService pvAttachmentService) {
		return fileUpload(request, null, pvAttachmentService);
	}
	public List<PVAttachmentEntity> fileUpload(HttpServletRequest request,String attachGroupId, PVAttachmentService pvAttachmentService) {
		
		Properties fileProp = getProperty("config/file.properties");
		
		String fileType = fileProp.getProperty("file_type");
		Integer maxSize = Integer.valueOf(fileProp.getProperty("max_size"));
		maxSize = 1024 * 1024 * maxSize;
//		response.setHeader("Access-Control-Allow-Origin", "*");
		//文件要保存的路径
		String datePath = DateUtils.getCurrentDate(DateUtils.FORMAT1);
		String savePath =fileProp.getProperty("save_path") + datePath;
//		//System.out.println("上传的路径："+savePath);
//		response.setContentType("text/html;charset=UTF-8");
		//检查目录
		File uploadDir = null;
		if(!savePath.contains("..")) {
			uploadDir = new File(savePath);
		} else {
			throw new RuntimeException("path invalid");
		}
		if(!uploadDir.exists()){
			boolean mkf = uploadDir.mkdirs();
			if(!mkf) {
				throw new RuntimeException("创建文件夹失败");
			}
		}
		if(!uploadDir.canWrite()){
			throw new RuntimeException("上传的目录没有写的权限");
		}
		
		DiskFileItemFactory factory = new DiskFileItemFactory();
		factory.setSizeThreshold(1024*1024);//设置缓冲区大小，这里是1m
		factory.setRepository(uploadDir);//设置缓冲区目录
		
		ServletFileUpload upload = new ServletFileUpload(factory);
		
		upload.setHeaderEncoding("UTF-8");
		
		if(!ServletFileUpload.isMultipartContent(request)){
			throw new RuntimeException( "非文件上传类型");
		}
		List<FileItem> items = null;
		try {
			items = upload.parseRequest(request);
		} catch (FileUploadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		
//		Iterator it  = items.iterator();
//		
//		FileItem item = null;
		
		String fileName = "";
		String name = "";
		String extName = "";
		String newFileName = "";
		if(items.isEmpty()){
			throw new RuntimeException("未选中任何文件");
		}
		
		
		List<PVAttachmentEntity> saveAttList = new ArrayList<PVAttachmentEntity>();
		String groupId = "";
		if(StringUtils.isNotBlank(attachGroupId)){
			groupId = attachGroupId;
		}else{
		groupId = UUID.randomUUID().toString().replace("-", "");//附件组id
		}
		for(FileItem item:items){
			
			if(item.isFormField()){
//				//System.out.println("------文本域----");
//				//System.out.println("fieldName:"+item.getFieldName());
//				//System.out.println("content:"+item.getString());
//				//System.out.println("文件名："+item.getName());
			}
			else
			{//如果是文件类型的数据
				
				fileName = item.getName();
					if(!StringUtils.isNotBlank(fileName)){
						continue;
					   }
			
			       //判断文件大小
					if(item.getSize() > maxSize){
						item.delete();
						throw new RuntimeException("文件大小超过限制！应小于"+maxSize/1024/1024+"M");
					   }
			
//				//System.out.println("文件名称："+fileName);
			    //获取文件名称
			    name = fileName.substring(fileName.lastIndexOf("\\")+ 1,fileName.lastIndexOf("."));
			
			    //获取文件后缀名
			
			    extName = fileName.substring(fileName.lastIndexOf(".")+ 1).toLowerCase(Locale.ENGLISH).trim();
//			    //System.out.println("后缀名："+extName);
			    	if(!Arrays.<String>asList(fileType.split(",")).contains(extName)){
			    		item.delete();
			    		throw new RuntimeException("文件类型不正确");
			    	  }
			
			    newFileName = IDGenerator.init() + "." + extName;
			    String lastSavePath = savePath + "/" +extName;
			    File lastFile = null;
			    if(!lastSavePath.contains("..")) {
			    		lastFile = new File(lastSavePath);
			    } else {
			    		throw new RuntimeException("path invalid");
			    }
			    if(!lastFile.exists()){
			    		if(!lastFile.mkdirs()) 
			    			throw new RuntimeException("path invalid");
			    }
			    File uploadedFile = null;
			    if(!lastSavePath.contains("..")) {
			    		uploadedFile = new File(lastSavePath,newFileName);
			    } else {
			    		throw new RuntimeException("path invalid");
			    }
			    try {
					item.write(uploadedFile);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			    
			    //往附件表存数据
			    PVAttachmentEntity attachment = new PVAttachmentEntity();
			    String attachId = UUID.randomUUID().toString().toUpperCase().replace("-", "");
			    attachment.setAttachId(attachId);
			    attachment.setGroupId(groupId);
			    attachment.setAttachName(name);
			    attachment.setAttachSize(item.getSize());
			    attachment.setAttachType(extName);
			    String updatedSavePath = datePath + "/"+extName+"/"+newFileName;
			    attachment.setSavePath(updatedSavePath);
			    attachment.setCreateBy(null);
			    attachment.setCreateDate(DateUtils.getCurrentDate(DateUtils.FORMAT7));
			    saveAttList.add(attachment);
			}
		}
		
		if(!saveAttList.isEmpty()){
			pvAttachmentService.batchAddAttach(saveAttList);
		}
		return saveAttList;
	}
	
	/**
	 * @描述 文件下载
	 * @创建时间 2017年8月9日11:53:23
	 * @param filePath //完整文件路径（包括文件名和拓展名）
	 * @param fileName //下载后看到的文件名
	 * */
	public void downFile(HttpServletRequest request,HttpServletResponse response,String filePath,String attachName){
		Properties fileProp = getProperty("config/file.properties");
		String basePath = fileProp.getProperty("save_path");
		filePath = basePath + filePath;
		File file = null;
		if(!filePath.contains("..")&&!basePath.contains("..")) {
			file = new File(filePath);
		} else {
			try {
				response.sendError(404, "File not found!");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		//filePath是指欲下载的文件的路径
		if(!file.exists()){
			try {
				response.sendError(404, "File not found!");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            return;
		}
		
		//取得文件名
		String fileName = file.getName();
		
		//取得文件的后缀名
		
		String extName = fileName.substring(fileName.lastIndexOf(".")+ 1).toUpperCase();
		
		//以流的形式下载文件
		
		InputStream fis = null;
		try {
			fis = new BufferedInputStream(new FileInputStream(filePath));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			if(fis != null)
				try {
					fis.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		
		byte[] buffer = null;
		try {
			buffer = new byte[fis.available()];
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		int n = 0;
		try {
			n = fis.read(buffer);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(n == 0) {
			//...
		}
		
		//清空response
		response.reset();
		//设置response的header
		String filename = "";
		if(StringUtils.isNotBlank(attachName)){
			String agent = request.getHeader("USER-AGENT");
			if (null != agent && -1 != agent.indexOf("MSIE")) {// IE
				try {
					filename = java.net.URLEncoder.encode(attachName, "UTF-8") + "."+ extName;
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (null != agent && -1 != agent.indexOf("Mozilla")) {
				try {
					filename = new String(attachName.getBytes("UTF-8"), "iso-8859-1") + "." + extName;
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				try {
					filename = java.net.URLEncoder.encode(attachName, "UTF-8") + "." + extName;
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}else{
			filename = new String(fileName.getBytes());
		}
		
		if(!Pattern.matches("[\\n\\r\\\\]+", filename)) {
			response.setHeader("Content-Disposition", "attachment;filename="+ filename);
		}
		String length = String.valueOf(file.length());
		if(!Pattern.matches("[\\n\\r\\\\]+", length)) {
			response.setHeader("Content-Length", length);
		}
		
		OutputStream toClient = null;
		try {
			toClient = new BufferedOutputStream(response.getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		response.setContentType("application/octet-stream");
		try {
			toClient.write(buffer);
			toClient.flush();
			toClient.close();
			fis.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;
	}
	
	
	/**
	 * @描述 得到配置文件的内容
	 * @创建时间 2017年8月10日09:28:08
	 * 
	 * */
	public static  Properties getProperty(String path){
		Properties prop = new Properties();
		InputStream in = null;
		try {
			in = FileSetModel.class.getClassLoader().getResourceAsStream(path);
			prop.load(in);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(in != null)
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		return prop;
	}

	/**
	 * @描述 获取文件保存的路径
	 * @创建时间 2017年8月11日15:34:11
	 * @param relaPath String 相对路径
	 * */
	public String getPath(String relaPath){
		Properties prop = getProperty("config/file.properties");
		String basePath = prop.getProperty("save_path");
		return basePath+relaPath;
	}
	
	public static void setFileDownloadHeader(HttpServletRequest request, HttpServletResponse response,
			String fileName) {
		try {
			// 中文文件名支持
			String encodedfileName = null;
			String agent = request.getHeader("USER-AGENT");
			if (null != agent && -1 != agent.indexOf("MSIE")) {// IE
				encodedfileName = java.net.URLEncoder.encode(fileName, "UTF-8");
			} else if (null != agent && -1 != agent.indexOf("Mozilla")) {
				encodedfileName = new String(fileName.getBytes("UTF-8"), "iso-8859-1");
			} else {
				encodedfileName = java.net.URLEncoder.encode(fileName, "UTF-8");
			}
			response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedfileName + "\"");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
}
