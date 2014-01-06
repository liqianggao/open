package com.mapzen.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.mapzen.R;
import com.mapzen.osrm.Instruction;

import org.oscim.map.Map;

import java.util.ArrayList;

public class RouteFragment extends Fragment {
    public static final int ROUTE_ZOOM_LEVEL = 19;
    public static final float ROUTE_TILT_LEVEL = 150.0f;
    private ArrayList<Instruction> instructions;
    private MapFragment mapFragment;
    private TextView title, street;
    private int routeIndex;
    private Button nextBtn;

    public void setInstructions(ArrayList<Instruction> instructions) {
        this.instructions = instructions;
    }

    public void setMapFragment(MapFragment mapFragment) {
        this.mapFragment = mapFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        routeIndex = 0;
        View rootView = inflater.inflate(R.layout.route_widget, container, false);
        FrameLayout frame = (FrameLayout) container;
        frame.setVisibility(View.VISIBLE);
        title = (TextView) rootView.findViewById(R.id.instruction_title);
        street = (TextView) rootView.findViewById(R.id.instruction_street);
        nextBtn = (Button) rootView.findViewById(R.id.next_btn);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                routeIndex++;
                setRoute(routeIndex);
            }
        });
        setRoute(routeIndex);
        return rootView;
    }

    private void setRoute(int index) {
        title.setText(instructions.get(index).getHumanTurnInstruction());
        street.setText(instructions.get(index).getName());

        double[] firstPoint = instructions.get(index).getPoint();
        Map map = mapFragment.getMap();
        map.setMapPosition(firstPoint[0], firstPoint[1], Math.pow(2, ROUTE_ZOOM_LEVEL));
        map.getViewport().setTilt(ROUTE_TILT_LEVEL);
        map.getViewport().setRotation(instructions.get(index).getBearing());
    }
}