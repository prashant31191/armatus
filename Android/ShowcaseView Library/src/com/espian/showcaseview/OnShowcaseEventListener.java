package com.espian.showcaseview;

/**
 * @author Alex
 */
public interface OnShowcaseEventListener {
	void onShowcaseViewHide(ShowcaseView showcaseView);
	void onShowcaseViewDidHide(ShowcaseView showcaseView);
	void onShowcaseViewShow(ShowcaseView showcaseView);

	/**
	 * Empty implementation of OnShowcaseViewEventListener such that null
	 * checks aren't needed
	 */
	public static final OnShowcaseEventListener NONE = new OnShowcaseEventListener() {
		@Override
		public void onShowcaseViewHide(ShowcaseView showcaseView) {}

		@Override
		public void onShowcaseViewDidHide(ShowcaseView showcaseView) {}

		@Override
		public void onShowcaseViewShow(ShowcaseView showcaseView) {}
	};
}