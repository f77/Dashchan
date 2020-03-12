/*
 * Copyright 2014-2018 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.util;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Browser;
import android.util.Pair;
import android.view.View;

import androidx.browser.customtabs.CustomTabsIntent;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.FileProvider;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.net.CloudFlarePasser;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.media.VideoPlayer;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.LauncherActivity;
import com.mishiranu.dashchan.ui.WebBrowserActivity;
import com.mishiranu.dashchan.ui.gallery.GalleryActivity;
import com.mishiranu.dashchan.ui.navigator.NavigatorActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.http.CookieBuilder;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class NavigationUtils {
	public static final int FLAG_NOT_ANIMATED = 0x00000001;
	public static final int FLAG_FROM_CACHE = 0x00000002;
	public static final int FLAG_RETURNABLE = 0x0000004;
	public static final int FLAG_LAUNCHER = 0x00000008;

	private static Intent obtainMainIntent(Context context, int flags, int allowFlags) {
		return new Intent().setComponent(new ComponentName(context, NavigatorActivity.class))
				.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
				.putExtra(C.EXTRA_NAVIGATION_FLAGS, flags & allowFlags);
	}

	public static Intent obtainThreadsIntent(Context context, String chanName, String boardName, int flags) {
		int allowFlags = FLAG_NOT_ANIMATED | FLAG_FROM_CACHE | FLAG_RETURNABLE | FLAG_LAUNCHER;
		return obtainMainIntent(context, flags, allowFlags).putExtra(C.EXTRA_CHAN_NAME, chanName)
				.putExtra(C.EXTRA_BOARD_NAME, boardName);
	}

	public static Intent obtainPostsIntent(Context context, String chanName, String boardName, String threadNumber,
										   String postNumber, String threadTitle, int flags) {
		int allowFlags = FLAG_NOT_ANIMATED | FLAG_FROM_CACHE | FLAG_RETURNABLE;
		return obtainMainIntent(context, flags, allowFlags).putExtra(C.EXTRA_CHAN_NAME, chanName)
				.putExtra(C.EXTRA_BOARD_NAME, boardName).putExtra(C.EXTRA_THREAD_NUMBER, threadNumber)
				.putExtra(C.EXTRA_POST_NUMBER, postNumber).putExtra(C.EXTRA_THREAD_TITLE, threadTitle);
	}

	public static Intent obtainSearchIntent(Context context, String chanName, String boardName, String searchQuery,
											int flags) {
		int allowFlags = FLAG_NOT_ANIMATED | FLAG_RETURNABLE;
		return obtainMainIntent(context, flags, allowFlags).putExtra(C.EXTRA_CHAN_NAME, chanName)
				.putExtra(C.EXTRA_BOARD_NAME, boardName).putExtra(C.EXTRA_SEARCH_QUERY, searchQuery);
	}

	public static Intent obtainTargetIntent(Context context, String chanName, ChanLocator.NavigationData data,
											int flags) {
		switch (data.target) {
			case ChanLocator.NavigationData.TARGET_THREADS: {
				return obtainThreadsIntent(context, chanName, data.boardName, flags);
			}
			case ChanLocator.NavigationData.TARGET_POSTS: {
				return obtainPostsIntent(context, chanName, data.boardName, data.threadNumber, data.postNumber,
						null, flags);
			}
			case ChanLocator.NavigationData.TARGET_SEARCH: {
				return obtainSearchIntent(context, chanName, data.boardName, data.searchQuery, flags);
			}
			default: {
				throw new IllegalStateException();
			}
		}
	}

	public static void handleGalleryUpButtonClick(Activity activity, boolean overrideUpButton,
												  String chanName, GalleryItem galleryItem) {
		boolean success = false;
		if (overrideUpButton) {
			String boardName = null, threadNumber = null;
			if (galleryItem != null) {
				boardName = galleryItem.boardName;
				threadNumber = galleryItem.threadNumber;
			}
			if (threadNumber != null) {
				activity.finish();
				activity.startActivity(obtainPostsIntent(activity, chanName, boardName, threadNumber, null, null, 0));
				success = true;
			}
		}
		if (!success) {
			activity.finish();
		}
	}

	public static Activity getActivity(Context context) {
		while (!(context instanceof Activity) && context instanceof ContextWrapper) {
			context = ((ContextWrapper) context).getBaseContext();
		}
		if (context instanceof Activity) {
			return (Activity) context;
		}
		return null;
	}

	public enum BrowserType {AUTO, INTERNAL, EXTERNAL}

	public static void handleUri(Context context, String chanName, Uri uri, BrowserType browserType) {
		if (chanName != null) {
			uri = ChanLocator.get(chanName).convert(uri);
		}
		boolean isWeb = ChanLocator.getDefault().isWebScheme(uri);
		Intent intent;
		boolean internalBrowser = isWeb && (browserType == BrowserType.INTERNAL || browserType == BrowserType.AUTO &&
				Preferences.isUseInternalBrowser());
		boolean chromeTabs = Preferences.isUseChromeTabs();
		if (internalBrowser && browserType != BrowserType.INTERNAL) {
			ChanManager manager = ChanManager.getInstance();
			PackageManager packageManager = context.getPackageManager();
			HashSet<ComponentName> names = new HashSet<>();
			List<ResolveInfo> infos = packageManager.queryIntentActivities(new Intent(Intent.ACTION_VIEW, uri),
					PackageManager.MATCH_DEFAULT_ONLY);
			for (ResolveInfo info : infos) {
				ActivityInfo activityInfo = info.activityInfo;
				String packageName = activityInfo.applicationInfo.packageName;
				if (!manager.isExtensionPackage(packageName)) {
					names.add(new ComponentName(packageName, activityInfo.name));
				}
			}
			infos = packageManager.queryIntentActivities(new Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com")),
					PackageManager.MATCH_DEFAULT_ONLY);
			for (ResolveInfo info : infos) {
				ActivityInfo activityInfo = info.activityInfo;
				names.remove(new ComponentName(activityInfo.applicationInfo.packageName, activityInfo.name));
			}
			internalBrowser = names.size() == 0;
		}

		if (internalBrowser & chromeTabs) {
			intent = null;
			CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
			int color = ResourceUtils.getColor(context, R.attr.colorPrimarySupport);
			builder.setToolbarColor(color);
			CustomTabsIntent customTabsIntent = builder.build();
			customTabsIntent.launchUrl(context, uri);
		} else if (internalBrowser) {
			intent = new Intent(context, WebBrowserActivity.class).setData(uri);
			if (getActivity(context) == null) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}
		} else {
			intent = new Intent(Intent.ACTION_VIEW, uri);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
			intent.putExtra(C.EXTRA_FROM_CLIENT, true);
			if (chanName != null && ChanLocator.get(chanName).safe(false).isAttachmentUri(uri)) {
				String cloudFlareCookie = CloudFlarePasser.getCookie(chanName);
				if (cloudFlareCookie != null) {
					// For MX Player, see https://sites.google.com/site/mxvpen/api
					String userAgent = AdvancedPreferences.getUserAgent(chanName);
					String cookie = new CookieBuilder().append(CloudFlarePasser.COOKIE_CLOUDFLARE,
							cloudFlareCookie).build();
					intent.putExtra("headers", new String[]{"User-Agent", userAgent, "Cookie", cookie});
				}
			}
			if (!isWeb) {
				intent = Intent.createChooser(intent, null);
			}
		}

		try {
			context.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			ToastUtils.show(context, R.string.message_unknown_address);
		} catch (NullPointerException e) {
			// @TODO: please fix this shit
			// @TODO: какой-то внутренний пердолинг android, хз.
			// @see https://stackoverflow.com/questions/17549030/android-nullpointerexception-in-instrumentation-execstartactivity
		} catch (Exception e) {
			ToastUtils.show(context, e.getMessage());
		}
	}

	public static void handleUriInternal(Context context, String chanName, Uri uri, boolean allowExpandedScreen) {
		String uriChanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
		if (uriChanName != null) {
			chanName = uriChanName;
		}
		ChanLocator locator = ChanLocator.get(chanName);
		boolean handled = false;
		if (chanName != null && locator.safe(false).isAttachmentUri(uri)) {
			Uri internalUri = locator.convert(uri);
			String fileName = locator.createAttachmentFileName(internalUri);
			if (locator.isImageUri(internalUri) || locator.isVideoUri(internalUri) && isOpenableVideoPath(fileName)) {
				openImageVideo(context, internalUri, allowExpandedScreen);
				handled = true;
			} else if (locator.isAudioUri(internalUri)) {
				AudioPlayerService.start(context, chanName, internalUri, fileName);
				handled = true;
			}
		}
		if (!handled && locator.isWebScheme(uri)) {
			String path = uri.getPath();
			if (locator.isImageExtension(path) || locator.isVideoExtension(path) && isOpenableVideoPath(path)) {
				openImageVideo(context, uri, allowExpandedScreen);
				handled = true;
			}
		}
		if (!handled) {
			handleUri(context, chanName, uri, BrowserType.AUTO);
		}
	}

	public enum NavigatePostMode {DISABLED, MANUALLY, ENABLED}

	private static WeakReference<ArrayList<GalleryItem>> galleryItems;

	public static void openGallery(Context context, View imageView, String chanName, int imageIndex,
								   GalleryItem.GallerySet gallerySet, boolean allowExpandedScreen, NavigatePostMode navigatePostMode,
								   boolean galleryMode) {
		int[] viewPosition = null;
		if (imageView != null) {
			int[] location = new int[2];
			imageView.getLocationOnScreen(location);
			viewPosition = new int[] {location[0], location[1], imageView.getWidth(), imageView.getHeight()};
		}
		gallerySet.cleanup();
		ArrayList<GalleryItem> galleryItems = gallerySet.getItems();
		NavigationUtils.galleryItems = new WeakReference<>(galleryItems);
		Intent intent = new Intent(context, GalleryActivity.class);
		intent.putExtra(C.EXTRA_CHAN_NAME, chanName);
		intent.putExtra(C.EXTRA_THREAD_TITLE, gallerySet.getThreadTitle());
		intent.putExtra(C.EXTRA_OBTAIN_ITEMS, true);
		intent.putExtra(C.EXTRA_IMAGE_INDEX, imageIndex);
		intent.putExtra(C.EXTRA_ALLOW_EXPANDED_SCREEN, allowExpandedScreen);
		intent.putExtra(C.EXTRA_NAVIGATE_POST_MODE, (gallerySet.isNavigatePostSupported()
				? navigatePostMode : NavigatePostMode.DISABLED).name());
		intent.putExtra(C.EXTRA_GALLERY_MODE, galleryMode);
		intent.putExtra(C.EXTRA_VIEW_POSITION, viewPosition);
		context.startActivity(intent);
		synchronized (NavigationUtils.class) {
			File file = getSerializedImagesFile(context);
			file.delete();
			// Serialize images to load them if process was killed
			// I can't pass images in parcel because it can cause TransactionTooLargeException
			new Thread(new SerializeGalleryItems(galleryItems, file)).start();
		}
	}

	@SuppressWarnings("unchecked")
	public static ArrayList<GalleryItem> obtainImagesProvider(Context context) {
		ArrayList<GalleryItem> galleryItems = NavigationUtils.galleryItems != null
				? NavigationUtils.galleryItems.get() : null;
		if (galleryItems == null) {
			synchronized (NavigationUtils.class) {
				FileInputStream input = null;
				try {
					input = new FileInputStream(getSerializedImagesFile(context));
					galleryItems = (ArrayList<GalleryItem>) new ObjectInputStream(input).readObject();
				} catch (Exception e) {
					// Ignore exception
				} finally {
					IOUtils.close(input);
				}
			}
		}
		return galleryItems;
	}

	private static File getSerializedImagesFile(Context context) {
		return new File(context.getCacheDir(), "images");
	}

	private static class SerializeGalleryItems implements Runnable {
		private final ArrayList<GalleryItem> galleryItems;
		private final File file;

		public SerializeGalleryItems(ArrayList<GalleryItem> galleryItems, File file) {
			this.galleryItems = galleryItems;
			this.file = file;
		}

		@Override
		public void run() {
			synchronized (NavigationUtils.class) {
				FileOutputStream output = null;
				try {
					output = new FileOutputStream(file);
					new ObjectOutputStream(output).writeObject(galleryItems);
				} catch (Exception e) {
					// Ignore exception
				} finally {
					IOUtils.close(output);
				}
			}
		}
	}

	public static void openImageVideo(Context context, Uri uri, boolean allowExpandedScreen) {
		context.startActivity(new Intent(context, GalleryActivity.class).setData(uri)
				.putExtra(C.EXTRA_ALLOW_EXPANDED_SCREEN, allowExpandedScreen));
	}

	public static boolean isOpenableVideoPath(String path) {
		return isOpenableVideoExtension(StringUtils.getFileExtension(path));
	}

	public static boolean isOpenableVideoExtension(String extension) {
		return Preferences.isUseVideoPlayer() && VideoPlayer.isLoaded() &
				C.OPENABLE_VIDEO_EXTENSIONS.contains(extension);
	}

	public static void searchImage(Context context, final String chanName, Uri uri) {
		ChanLocator locator = ChanLocator.get(chanName);
		final String imageUriString = locator.convert(uri).toString();
		new DialogMenu(context, new DialogMenu.Callback() {
			@Override
			public void onItemClick(Context context, int id, Map<String, Object> extra) {
				ChanLocator locator = ChanLocator.getDefault();
				Uri uri;
				switch (id) {
					case 0: {
						uri = locator.buildQueryWithHost("www.google.com", "searchbyimage",
								"image_url", imageUriString);
						break;
					}
					case 1: {
						uri = locator.buildQueryWithHost("yandex.ru", "images/search", "rpt", "imageview",
								"url", imageUriString);
						break;
					}
					case 2: {
						uri = locator.buildQueryWithHost("www.tineye.com", "search", "url", imageUriString);
						break;
					}
					case 3: {
						uri = locator.buildQueryWithHost("saucenao.com", "search.php", "url", imageUriString);
						break;
					}
					case 4: {
						uri = locator.buildQueryWithSchemeHost(false, "iqdb.org", null, "url", imageUriString);
						break;
					}
					case 5: {
						uri = locator.buildQueryWithHost("whatanime.ga", "/", "url", imageUriString);
						break;
					}
					default: {
						return;
					}
				}
				handleUri(context, null, uri, BrowserType.EXTERNAL);
			}
		})
				.addItem(0, "Google")
				.addItem(1, "Yandex")
				.addItem(2, "TinEye")
				.addItem(3, "SauceNAO")
				.addItem(4, "iqdb")
				.addItem(5, "whatanime")
				.show();
	}

	public static void shareText(Context context, String subject, String text, Uri uri) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_SUBJECT, subject);
		intent.putExtra(Intent.EXTRA_TEXT, text);
		intent = Intent.createChooser(intent, null);
		if (uri != null) {
			List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities
					(new Intent(Intent.ACTION_VIEW).setData(uri), PackageManager.MATCH_DEFAULT_ONLY);
			if (activities != null && !activities.isEmpty()) {
				HashSet<String> filterPackageNames = new HashSet<>();
				filterPackageNames.add(context.getPackageName());
				for (ChanManager.ExtensionItem extensionItem : ChanManager.getInstance().getExtensionItems()) {
					filterPackageNames.add(extensionItem.packageInfo.packageName);
				}
				ArrayList<Intent> browserIntents = new ArrayList<>();
				for (ResolveInfo resolveInfo : activities) {
					if (!filterPackageNames.contains(resolveInfo.activityInfo.packageName)) {
						browserIntents.add(new Intent(Intent.ACTION_VIEW).setData(uri)
								.setComponent(new ComponentName(resolveInfo.activityInfo.packageName,
										resolveInfo.activityInfo.name)));
					}
				}
				if (!browserIntents.isEmpty()) {
					intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, CommonUtils.toArray(browserIntents, Intent.class));
				}
			}
		}
		context.startActivity(intent);
	}

	public static void shareLink(Context context, String subject, Uri uri) {
		shareText(context, subject, uri.toString(), uri);
	}

	public static void shareFile(Context context, File file, String fileName) {
		Pair<Uri, String> data = CacheManager.getInstance().prepareFileForShare(file, fileName);
		if (data == null) {
			ToastUtils.show(context, R.string.message_cache_unavailable);
			return;
		}
		int intentFlags = FileProvider.getIntentFlags();
		context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
				.setType(data.second).setFlags(intentFlags).putExtra(Intent.EXTRA_STREAM, data.first), null));
	}

	public static void restartApplication(Context context) {
		try {
			CacheManager.getInstance().waitSerializationFinished();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		Intent intent = new Intent(context, LauncherActivity.class).setAction(Intent.ACTION_MAIN)
				.addCategory(Intent.CATEGORY_LAUNCHER);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, pendingIntent);
		System.exit(0);
	}
}
